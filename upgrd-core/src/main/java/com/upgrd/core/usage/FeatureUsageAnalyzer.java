package com.upgrd.core.usage;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.BrokenAccessSignal;
import com.upgrd.core.model.DeployPresence;
import com.upgrd.core.model.FeatureHealth;
import com.upgrd.core.model.FeatureKind;
import com.upgrd.core.model.FeatureUsageEntry;
import com.upgrd.core.model.FeatureUsageReport;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageObservation;
import com.upgrd.core.model.UsageReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FeatureUsageAnalyzer {

    private static final Pattern STRUTS_ACTION = Pattern.compile(
            "<action\\s+[^>]*path\\s*=\\s*\"([^\"]+)\"[^>]*type\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUTS_ACTION_ALT = Pattern.compile(
            "<action\\s+[^>]*type\\s*=\\s*\"([^\"]+)\"[^>]*path\\s*=\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    public FeatureUsageReport analyze(
            Path sourceRoot,
            ProjectDiscovery discovery,
            SyncReport sync,
            UsageReport usage,
            Set<String> warClasses,
            Set<String> sourceClasses) throws IOException {
        Map<String, HitInfo> hitIndex = indexHits(usage);
        Map<String, BrokenInfo> brokenIndex = indexBroken(usage);
        List<FeatureUsageEntry> features = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        addApplicationClasses(features, seenIds, warClasses, sourceClasses, hitIndex, brokenIndex);
        addStrutsActions(features, seenIds, sourceRoot, discovery, warClasses, sourceClasses, hitIndex, brokenIndex);
        addJspPages(features, seenIds, sourceRoot, discovery, hitIndex, brokenIndex);
        addOrphanPathHits(features, seenIds, hitIndex, brokenIndex);

        features.sort(Comparator
                .comparingInt((FeatureUsageEntry entry) -> healthRank(entry.health()))
                .thenComparing(FeatureUsageEntry::observation)
                .thenComparing(entry -> -entry.errorHitCount())
                .thenComparing(entry -> -entry.hitCount())
                .thenComparing(FeatureUsageEntry::kind)
                .thenComparing(FeatureUsageEntry::name));

        int observed = (int) features.stream()
                .filter(f -> f.observation() == UsageObservation.OBSERVED)
                .count();
        int unobserved = features.size() - observed;
        int healthy = (int) features.stream().filter(f -> f.health() == FeatureHealth.HEALTHY).count();
        int broken = (int) features.stream().filter(f -> f.health() == FeatureHealth.BROKEN).count();

        return new FeatureUsageReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                usage != null ? usage.logFileCount() : 0,
                features.size(),
                observed,
                unobserved,
                healthy,
                broken,
                countByKind(features, UsageObservation.OBSERVED),
                countByKind(features, UsageObservation.UNOBSERVED),
                usage != null ? usage.hitsByLogKind() : Map.of(),
                usage != null ? usage.logSources() : List.of(),
                List.copyOf(features),
                buildNotes(usage, sync, observed, unobserved, broken));
    }

    private void addApplicationClasses(
            List<FeatureUsageEntry> features,
            Set<String> seenIds,
            Set<String> warClasses,
            Set<String> sourceClasses,
            Map<String, HitInfo> hitIndex,
            Map<String, BrokenInfo> brokenIndex) {
        Set<String> allClasses = new HashSet<>();
        allClasses.addAll(warClasses);
        allClasses.addAll(sourceClasses);

        for (String className : allClasses.stream().sorted().toList()) {
            String id = "class:" + className;
            if (!seenIds.add(id)) {
                continue;
            }
            features.add(entry(
                    id,
                    FeatureKind.APPLICATION_CLASS,
                    className,
                    null,
                    deployPresence(className, warClasses, sourceClasses),
                    hitIndex.get(className),
                    brokenIndex.get(className),
                    classGuidance(hitIndex.get(className), brokenIndex.get(className),
                            deployPresence(className, warClasses, sourceClasses))));
        }
    }

    private void addStrutsActions(
            List<FeatureUsageEntry> features,
            Set<String> seenIds,
            Path sourceRoot,
            ProjectDiscovery discovery,
            Set<String> warClasses,
            Set<String> sourceClasses,
            Map<String, HitInfo> hitIndex,
            Map<String, BrokenInfo> brokenIndex) throws IOException {
        for (Path config : findStrutsConfigs(sourceRoot, discovery)) {
            String xml = Files.readString(config);
            collectStrutsActions(xml).forEach((path, actionClass) -> {
                String id = "struts:" + path;
                if (!seenIds.add(id)) {
                    return;
                }
                HitInfo combined = mergeHits(hitIndex.get(actionClass), hitIndex.get("path:" + path));
                BrokenInfo broken = mergeBroken(brokenIndex.get(actionClass), brokenIndex.get("path:" + path));
                DeployPresence presence = deployPresence(actionClass, warClasses, sourceClasses);
                features.add(entry(
                        id,
                        FeatureKind.STRUTS_ACTION,
                        path,
                        actionClass,
                        presence,
                        combined,
                        broken,
                        strutsGuidance(combined, broken, presence)));
            });
        }
    }

    private void addJspPages(
            List<FeatureUsageEntry> features,
            Set<String> seenIds,
            Path sourceRoot,
            ProjectDiscovery discovery,
            Map<String, HitInfo> hitIndex,
            Map<String, BrokenInfo> brokenIndex) throws IOException {
        for (Path jsp : findJspPages(sourceRoot, discovery)) {
            String relative = sourceRoot.relativize(jsp).toString().replace('\\', '/');
            String webPath = toWebPath(relative);
            String id = "jsp:" + webPath;
            if (!seenIds.add(id)) {
                continue;
            }
            HitInfo pathHit = mergeHits(hitIndex.get("path:" + webPath), hitIndex.get("path:" + "/" + relative));
            BrokenInfo broken = mergeBroken(brokenIndex.get("path:" + webPath), brokenIndex.get("path:" + "/" + relative));
            features.add(entry(
                    id,
                    FeatureKind.JSP_PAGE,
                    webPath,
                    relative,
                    DeployPresence.CONFIG_ONLY,
                    pathHit,
                    broken,
                    pathGuidance(pathHit, broken, "JSP view")));
        }
    }

    private void addOrphanPathHits(
            List<FeatureUsageEntry> features,
            Set<String> seenIds,
            Map<String, HitInfo> hitIndex,
            Map<String, BrokenInfo> brokenIndex) {
        for (Map.Entry<String, HitInfo> hit : hitIndex.entrySet()) {
            if (!hit.getKey().startsWith("path:")) {
                continue;
            }
            String path = hit.getKey().substring("path:".length());
            String id = "path:" + path;
            if (!seenIds.add(id)) {
                continue;
            }
            features.add(entry(
                    id,
                    FeatureKind.HTTP_PATH,
                    path,
                    "from logs",
                    DeployPresence.CONFIG_ONLY,
                    hit.getValue(),
                    brokenIndex.get(hit.getKey()),
                    pathGuidance(hit.getValue(), brokenIndex.get(hit.getKey()), "HTTP path")));
        }
    }

    private FeatureUsageEntry entry(
            String id,
            FeatureKind kind,
            String name,
            String detail,
            DeployPresence presence,
            HitInfo hit,
            BrokenInfo broken,
            String guidance) {
        int hitCount = hit != null ? hit.hitCount() : 0;
        int errorHits = broken != null ? broken.errorHits() : 0;
        int successHits = broken != null ? broken.successHits() : 0;
        FeatureHealth health = resolveHealth(hitCount, errorHits);
        UsageObservation observation = health == FeatureHealth.UNOBSERVED
                ? UsageObservation.UNOBSERVED
                : UsageObservation.OBSERVED;
        if (health == FeatureHealth.BROKEN) {
            guidance = guidance + " — errors detected in access/server/out/application logs; investigate before cutover";
        }
        return new FeatureUsageEntry(
                id,
                kind,
                name,
                detail,
                observation,
                health,
                hitCount,
                errorHits,
                successHits,
                inferLogKinds(hit, broken),
                hit != null ? hit.sample() : null,
                broken != null ? broken.errorSample() : null,
                presence,
                guidance);
    }

    private FeatureHealth resolveHealth(int hitCount, int errorHits) {
        if (hitCount <= 0 && errorHits <= 0) {
            return FeatureHealth.UNOBSERVED;
        }
        if (errorHits > 0) {
            return FeatureHealth.BROKEN;
        }
        return FeatureHealth.HEALTHY;
    }

    private List<String> inferLogKinds(HitInfo hit, BrokenInfo broken) {
        List<String> kinds = new ArrayList<>();
        if (hit != null && hitCountPositive(hit)) {
            kinds.add("USAGE");
        }
        if (broken != null && broken.errorHits() > 0) {
            kinds.add(broken.sourceLogKind() != null ? broken.sourceLogKind() : "ERROR");
        }
        return List.copyOf(kinds);
    }

    private boolean hitCountPositive(HitInfo hit) {
        return hit.hitCount() > 0;
    }

    private Map<String, HitInfo> indexHits(UsageReport usage) {
        Map<String, HitInfo> index = new HashMap<>();
        if (usage == null || usage.hits() == null) {
            return index;
        }
        for (UsageHit hit : usage.hits()) {
            if (hit.qualifiedName() != null) {
                index.put(hit.qualifiedName(), new HitInfo(hit.hitCount(), hit.sample()));
            }
        }
        return index;
    }

    private Map<String, BrokenInfo> indexBroken(UsageReport usage) {
        Map<String, BrokenInfo> index = new HashMap<>();
        if (usage == null || usage.brokenSignals() == null) {
            return index;
        }
        for (BrokenAccessSignal signal : usage.brokenSignals()) {
            index.put(signal.featureKey(), new BrokenInfo(
                    signal.errorHits(),
                    signal.successHits(),
                    signal.sample(),
                    signal.sourceLogKind()));
        }
        return index;
    }

    private HitInfo mergeHits(HitInfo a, HitInfo b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new HitInfo(
                a.hitCount() + b.hitCount(),
                a.sample() != null ? a.sample() : b.sample());
    }

    private BrokenInfo mergeBroken(BrokenInfo a, BrokenInfo b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new BrokenInfo(
                a.errorHits() + b.errorHits(),
                a.successHits() + b.successHits(),
                a.errorSample() != null ? a.errorSample() : b.errorSample(),
                a.sourceLogKind() != null ? a.sourceLogKind() : b.sourceLogKind());
    }

    private DeployPresence deployPresence(String className, Set<String> war, Set<String> source) {
        boolean inWar = war.contains(className);
        boolean inSource = source.contains(className);
        if (inWar && inSource) {
            return DeployPresence.BOTH;
        }
        if (inWar) {
            return DeployPresence.WAR_ONLY;
        }
        if (inSource) {
            return DeployPresence.SOURCE_ONLY;
        }
        return DeployPresence.CONFIG_ONLY;
    }

    private String classGuidance(HitInfo hit, BrokenInfo broken, DeployPresence presence) {
        if (broken != null && broken.errorHits() > 0) {
            return "Class appears in error logs — migrate and fix runtime failures";
        }
        if (hit != null && hit.hitCount() > 0) {
            return "Migrate and prioritize smoke tests — observed in logs";
        }
        return switch (presence) {
            case WAR_ONLY -> "Migrate for production parity (WAR-only); schedule manual or seasonal regression";
            case SOURCE_ONLY -> "Migrate from source; confirm still deployed — no log evidence in window";
            case BOTH -> "Migrate; no log evidence in window — add manual regression before cutover";
            case CONFIG_ONLY -> "Review configuration reference; verify deployment scope";
        };
    }

    private String strutsGuidance(HitInfo hit, BrokenInfo broken, DeployPresence presence) {
        if (broken != null && broken.errorHits() > 0) {
            return "Struts action accessed but failing — migrate to Spring MVC and fix errors";
        }
        if (hit != null && hit.hitCount() > 0) {
            return "Migrate Struts action to Spring MVC; observed in logs";
        }
        return "Migrate action mapping; unobserved in log window — likely batch, admin, or seasonal flow";
    }

    private String pathGuidance(HitInfo hit, BrokenInfo broken, String label) {
        if (broken != null && broken.errorHits() > 0) {
            return label + " accessed with HTTP/runtime errors — include in fix-first UAT list";
        }
        if (hit != null && hit.hitCount() > 0) {
            return "Observed " + label + " in logs — include in UAT checklist";
        }
        return "Unobserved " + label + " — migrate if configured; add explicit regression test";
    }

    private Map<String, String> collectStrutsActions(String xml) {
        Map<String, String> actions = new LinkedHashMap<>();
        Matcher matcher = STRUTS_ACTION.matcher(xml);
        while (matcher.find()) {
            actions.putIfAbsent(matcher.group(1), matcher.group(2));
        }
        matcher = STRUTS_ACTION_ALT.matcher(xml);
        while (matcher.find()) {
            actions.putIfAbsent(matcher.group(2), matcher.group(1));
        }
        return actions;
    }

    private List<Path> findStrutsConfigs(Path sourceRoot, ProjectDiscovery discovery) throws IOException {
        List<Path> configs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("struts-config.xml"))
                    .forEach(configs::add);
        }
        if (configs.isEmpty() && discovery.webInfDescriptors().contains("struts-config.xml")) {
            Path candidate = sourceRoot.resolve("WEB-INF/struts-config.xml");
            if (Files.isRegularFile(candidate)) {
                configs.add(candidate);
            }
        }
        return configs;
    }

    private List<Path> findJspPages(Path sourceRoot, ProjectDiscovery discovery) throws IOException {
        List<Path> pages = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jsp"))
                    .forEach(pages::add);
        }
        return pages;
    }

    private String toWebPath(String relative) {
        if (relative.startsWith("pages/")) {
            return "/" + relative;
        }
        if (relative.contains("webapp/")) {
            return "/" + relative.substring(relative.indexOf("webapp/") + "webapp/".length());
        }
        return "/" + relative;
    }

    private Map<FeatureKind, Integer> countByKind(List<FeatureUsageEntry> features, UsageObservation observation) {
        Map<FeatureKind, Integer> counts = new EnumMap<>(FeatureKind.class);
        for (FeatureKind kind : FeatureKind.values()) {
            counts.put(kind, 0);
        }
        for (FeatureUsageEntry feature : features) {
            if (feature.observation() == observation) {
                counts.merge(feature.kind(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<String> buildNotes(
            UsageReport usage,
            SyncReport sync,
            int observed,
            int unobserved,
            int broken) {
        List<String> notes = new ArrayList<>();
        int logFiles = usage != null ? usage.logFileCount() : 0;
        notes.add("Log window: " + logFiles + " staged file(s) — absence in logs does not mean feature is retired");
        notes.add("Access, server, out, and application logs are analyzed together");
        notes.add("Unobserved features are still migrated by default for production parity");
        if (broken > 0) {
            notes.add(broken + " feature(s) accessed but show errors — fix during migration UAT");
        }
        if (sync != null && !sync.onlyInWar().isEmpty()) {
            notes.add(sync.onlyInWar().size() + " WAR-only class(es) may lack source — merge + manual port required");
        }
        if (unobserved > observed && logFiles > 0) {
            notes.add("Majority unobserved — extend log window or add seasonal/batch archives before cutover sign-off");
        }
        return List.copyOf(notes);
    }

    private record HitInfo(int hitCount, String sample) {
    }

    private record BrokenInfo(int errorHits, int successHits, String errorSample, String sourceLogKind) {
    }

    private int healthRank(FeatureHealth health) {
        return switch (health) {
            case BROKEN -> 0;
            case HEALTHY -> 1;
            case UNOBSERVED -> 2;
        };
    }
}
