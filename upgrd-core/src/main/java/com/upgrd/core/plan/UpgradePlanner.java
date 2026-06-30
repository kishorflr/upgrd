package com.upgrd.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ApiCompatibilityHit;
import com.upgrd.core.model.ApiCompatibilityReport;
import com.upgrd.core.model.ApiRemediationType;
import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.LoggingFramework;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.ServletApi;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.TechnologyFingerprint;
import com.upgrd.core.model.SecurityFinding;
import com.upgrd.core.model.SecurityReport;
import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.SyncSeverity;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class UpgradePlanner {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public UpgradePlan plan(
            ProjectDiscovery discovery,
            String targetJava,
            String productionServer,
            boolean dryRun,
            SecurityReport security) {
        return plan(discovery, targetJava, productionServer, dryRun, security, null, null, null);
    }

    public UpgradePlan plan(
            ProjectDiscovery discovery,
            String targetJava,
            String productionServer,
            boolean dryRun,
            SecurityReport security,
            SyncReport sync,
            UsageReport usage) {
        return plan(discovery, targetJava, productionServer, dryRun, security, sync, usage, null);
    }

    public UpgradePlan plan(
            ProjectDiscovery discovery,
            String targetJava,
            String productionServer,
            boolean dryRun,
            SecurityReport security,
            SyncReport sync,
            UsageReport usage,
            ApiCompatibilityReport apiCompatibility) {
        List<UpgradeStep> steps = new ArrayList<>();
        ProjectProfile profile = discovery.profile();
        TechnologyFingerprint fp = discovery.fingerprint();

        if (discovery.buildSystem() != BuildSystem.MAVEN) {
            String recipe = profile == ProjectProfile.LEGACY_WEB
                    ? "upgrd:ConvertAntWarToMaven"
                    : "upgrd:ConvertFlatToMaven";
            steps.add(step(
                    "convert-maven",
                    "build",
                    profile == ProjectProfile.LEGACY_WEB
                            ? "Convert Ant WAR layout to Maven multi-module structure"
                            : "Convert flat/Ant layout to Maven structure",
                    recipe,
                    "Non-Maven builds block dependency analysis, security scanning, and reproducible Java "
                            + targetJava + " migration",
                    List.of("buildSystem=" + discovery.buildSystem()),
                    StepMode.AUTOMATED));
        }

        steps.add(step(
                "upgrade-java",
                "language",
                "Upgrade source compatibility to Java " + targetJava,
                "upgrd:UpgradeToJava21",
                "Target runtime requires Java " + targetJava + "; language features and deprecated APIs must be updated",
                List.of("javaVersionHint=" + discovery.javaVersionHint()),
                StepMode.AUTOMATED));

        if (profile == ProjectProfile.LEGACY_WEB) {
            addLegacyWebSteps(steps, fp, targetJava);
        }

        if (profile == ProjectProfile.LEGACY_BACKEND) {
            addLegacyBackendSteps(steps, fp);
        }

        addSecurityRemediationSteps(steps, security);
        addWarSyncSteps(steps, sync, usage);
        addApiCompatibilitySteps(steps, apiCompatibility);
        addWarAuthoritativeMergeStep(steps, sync);

        if (discovery.containsWeblogicApi() || fp.servletApi() == ServletApi.JAVAX) {
            steps.add(step(
                    "portable-jakarta",
                    "api",
                    "Replace javax.* with jakarta.* and isolate WebLogic-specific APIs",
                    "upgrd:JavaxToJakarta",
                    "Jakarta EE namespace is required for modern servlet containers and Spring 6+",
                    fp.evidence().stream().filter(e -> e.contains("javax")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "weblogic-adapters",
                    "server",
                    "Generate deploy/weblogic overlays for production (" + productionServer + ")",
                    "upgrd:WebLogic14cDescriptors",
                    "Production deployment targets " + productionServer + "; bindings stay out of portable code",
                    List.of("containsWeblogicApi=" + discovery.containsWeblogicApi()),
                    StepMode.AUTOMATED));
        }

        steps.add(step(
                "wildfly-local",
                "server",
                "Generate deploy/wildfly profile for local verification",
                "upgrd:WildFlyLocalProfile",
                "Local WildFly profile enables edge-only verification before production WebLogic deploy",
                List.of("localServer=wildfly"),
                StepMode.AUTOMATED));

        steps.add(step(
                "security-verify",
                "security",
                "Run OWASP Dependency-Check and SpotBugs after migration",
                "upgrd:SecurityVerify",
                "Post-upgrade verification confirms no new CVEs were introduced",
                fp.evidence().stream().filter(e -> e.startsWith("classpath:")).limit(5).toList(),
                StepMode.AUTOMATED));

        steps.add(step(
                "test-scaffold",
                "testing",
                "Add JUnit 5 smoke tests inside migrated app for hot paths",
                "upgrd:GenerateSmokeTests",
                "Automated tests in app-web/src/test/java give agents and CI a baseline; hot paths from logs are prioritized",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        steps.add(step(
                "openrewrite-scaffold",
                "tooling",
                "Scaffold OpenRewrite config for deeper AST migrations",
                "upgrd:OpenRewriteScaffold",
                "File-level recipes cover safe transforms; OpenRewrite YAML enables optional AST passes when teams are ready",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        steps.add(step(
                "openrewrite-dry-run",
                "tooling",
                "Run OpenRewrite dry-run to preview AST migrations",
                "upgrd:OpenRewriteDryRun",
                "Dry-run validates OpenRewrite recipes before modifying sources; gate for optional full apply via `upgrd rewrite run`",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        steps.add(step(
                "openrewrite-apply",
                "tooling",
                "Apply OpenRewrite AST migrations (after dry-run passes)",
                "upgrd:OpenRewriteApply",
                "Advisory — run `upgrd rewrite run` when dry-run is clean; not auto-applied to avoid surprise AST changes",
                List.of("profile=" + profile),
                StepMode.ADVISORY));

        steps.add(step(
                "openrewrite-sql-scan",
                "security",
                "OpenRewrite search scan for SQL concatenation patterns",
                "upgrd:OpenRewriteSqlScan",
                "Run `upgrd rewrite run --recipe com.upgrd.migrated.SqlConcatenationScan --dry-run --force` after apply",
                List.of("CWE-89"),
                StepMode.ADVISORY));

        steps.add(step(
                "automation-ready",
                "tooling",
                "Embed AI/automation-friendly metadata in migrated application",
                "upgrd:AutomationReady",
                "Standard Maven test layout, upgrd-analysis.json, and AGENTS.md in migrated/ help future tools analyze the upgraded codebase",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        return new UpgradePlan(
                AnalyzeEngine.VERSION,
                dryRun,
                targetJava,
                productionServer,
                "wildfly",
                profile,
                enrichWithApiHits(enrichWithWarContext(steps, sync, usage), apiCompatibility));
    }

    private void addWarAuthoritativeMergeStep(List<UpgradeStep> steps, SyncReport sync) {
        if (sync == null || sync.severity() == SyncSeverity.NONE) {
            return;
        }
        if (sync.onlyInWar().isEmpty() && sync.onlyInWarLibs().isEmpty()) {
            return;
        }
        UpgradeStep mergeStep = step(
                "war-authoritative-merge",
                "sync",
                "Merge production WAR classes and WEB-INF/lib into migrated layout",
                "upgrd:WarAuthoritativeMerge",
                "Production WAR is authoritative — " + sync.severityReason(),
                List.of(
                        "severity=" + sync.severity(),
                        "war-only-classes=" + sync.onlyInWar().size(),
                        "war-only-libs=" + sync.onlyInWarLibs().size()),
                StepMode.AUTOMATED);
        int insertAt = 0;
        for (int i = 0; i < steps.size(); i++) {
            if ("convert-maven".equals(steps.get(i).id())) {
                insertAt = i + 1;
                break;
            }
        }
        steps.add(insertAt, mergeStep);
    }

    private void addApiCompatibilitySteps(List<UpgradeStep> steps, ApiCompatibilityReport api) {
        if (api == null || api.hits().isEmpty()) {
            return;
        }
        long manual = api.countByType(ApiRemediationType.MANUAL);
        long unsupported = api.countByType(ApiRemediationType.UNSUPPORTED);
        if (manual + unsupported > 0) {
            List<String> evidence = new ArrayList<>();
            evidence.add(api.summary());
            api.hits().stream()
                    .filter(h -> h.remediationType() == ApiRemediationType.MANUAL
                            || h.remediationType() == ApiRemediationType.UNSUPPORTED)
                    .limit(8)
                    .forEach(h -> evidence.add(h.file() + ":" + h.lineRange().getFirst()
                            + " — " + h.api() + " → " + h.replacement()));
            steps.add(step(
                    "api-manual-rewrite",
                    "api",
                    "Manual API rewrites required (catalog hits without automated recipe)",
                    "upgrd:ApiManualReview",
                    api.summary(),
                    List.copyOf(evidence),
                    StepMode.ADVISORY));
        }
    }

    private List<UpgradeStep> enrichWithApiHits(List<UpgradeStep> steps, ApiCompatibilityReport api) {
        if (api == null || api.hits().isEmpty()) {
            return steps;
        }
        Map<String, List<ApiCompatibilityHit>> byStep = new java.util.LinkedHashMap<>();
        for (ApiCompatibilityHit hit : api.hits()) {
            if (hit.planStepId() != null && !hit.planStepId().isBlank()) {
                byStep.computeIfAbsent(hit.planStepId(), k -> new ArrayList<>()).add(hit);
            }
        }
        if (byStep.isEmpty()) {
            return steps;
        }
        List<UpgradeStep> enriched = new ArrayList<>();
        for (UpgradeStep step : steps) {
            List<ApiCompatibilityHit> linked = byStep.get(step.id());
            if (linked == null || linked.isEmpty()) {
                enriched.add(step);
                continue;
            }
            List<String> evidence = new ArrayList<>(step.evidence());
            evidence.add("api-hits=" + linked.size());
            linked.stream().limit(5).forEach(h ->
                    evidence.add(h.file() + ":" + h.lineRange().getFirst() + " " + h.api()
                            + " → " + h.replacement()));
            enriched.add(rebuildStep(step, evidence, step.reason()
                    + " — " + linked.size() + " catalog hit(s) in source"));
        }
        return enriched;
    }

    private void addWarSyncSteps(List<UpgradeStep> steps, SyncReport sync, UsageReport usage) {
        if (sync == null || sync.severity() == SyncSeverity.NONE) {
            return;
        }
        if (sync.severity().ordinal() >= SyncSeverity.MEDIUM.ordinal()) {
            steps.add(step(
                    "war-source-sync",
                    "sync",
                    "Review WAR vs source drift (production is authoritative)",
                    "upgrd:WarSourceSyncReview",
                    sync.severityReason(),
                    syncEvidence(sync, usage),
                    StepMode.ADVISORY));
        }
        if (!sync.onlyInWarLibs().isEmpty()) {
            steps.add(step(
                    "war-lib-align",
                    "sync",
                    "Align source lib/ with production WEB-INF/lib dependencies",
                    "upgrd:WarLibAlign",
                    "Production WAR ships JARs missing from source lib — dependency drift blocks faithful upgrade",
                    sync.onlyInWarLibs().stream().limit(10).map(j -> "war-lib:" + j).toList(),
                    StepMode.ADVISORY));
        }
    }

    private List<String> syncEvidence(SyncReport sync, UsageReport usage) {
        List<String> evidence = new ArrayList<>();
        evidence.add("severity=" + sync.severity());
        sync.onlyInWar().stream().limit(5).forEach(c -> evidence.add("war-only:" + c));
        sync.onlyInSource().stream().limit(3).forEach(c -> evidence.add("source-only:" + c));
        if (usage != null && usage.hits() != null) {
            for (UsageHit hit : usage.hits()) {
                String name = hit.qualifiedName();
                if (name != null && sync.onlyInWar().contains(name)) {
                    evidence.add("log-hotpath:" + name + "(" + hit.hitCount() + ")");
                }
            }
        }
        return List.copyOf(evidence);
    }

    private List<UpgradeStep> enrichWithWarContext(
            List<UpgradeStep> steps, SyncReport sync, UsageReport usage) {
        if (sync == null || sync.onlyInWar().isEmpty()) {
            return steps;
        }
        List<UpgradeStep> enriched = new ArrayList<>();
        for (UpgradeStep step : steps) {
            if ("convert-maven".equals(step.id())) {
                List<String> evidence = new ArrayList<>(step.evidence());
                evidence.add("war-only-classes=" + sync.onlyInWar().size());
                sync.onlyInWar().stream().limit(5).forEach(c -> evidence.add("war:" + c));
                enriched.add(rebuildStep(step, evidence, step.reason()
                        + " — WAR has " + sync.onlyInWar().size() + " production-only class(es)"));
            } else if ("test-scaffold".equals(step.id()) && usage != null) {
                List<String> hotWarOnly = warOnlyHotPaths(sync, usage);
                if (!hotWarOnly.isEmpty()) {
                    List<String> evidence = new ArrayList<>(step.evidence());
                    evidence.addAll(hotWarOnly);
                    enriched.add(rebuildStep(step, evidence, step.reason()
                            + " — prioritize smoke tests for log hot paths present only in WAR"));
                } else {
                    enriched.add(step);
                }
            } else {
                enriched.add(step);
            }
        }
        return enriched;
    }

    private List<String> warOnlyHotPaths(SyncReport sync, UsageReport usage) {
        if (usage.hits() == null) {
            return List.of();
        }
        List<String> paths = new ArrayList<>();
        for (UsageHit hit : usage.hits()) {
            String name = hit.qualifiedName();
            if (name != null && sync.onlyInWar().contains(name)) {
                paths.add("war-hotpath:" + name + "(" + hit.hitCount() + ")");
            }
        }
        return paths.stream().limit(5).toList();
    }

    private UpgradeStep rebuildStep(UpgradeStep step, List<String> evidence, String reason) {
        return new UpgradeStep(
                step.id(),
                step.category(),
                step.description(),
                step.recipe(),
                reason,
                List.copyOf(evidence),
                step.mode(),
                step.classification());
    }

    private void addLegacyWebSteps(List<UpgradeStep> steps, TechnologyFingerprint fp, String targetJava) {
        if (fp.logging() == LoggingFramework.LOG4J_1 || fp.logging() == LoggingFramework.MIXED) {
            steps.add(step(
                    "migrate-log4j1",
                    "logging",
                    "Migrate log4j 1.x to SLF4J",
                    "upgrd:Log4j1ToSlf4j",
                    "Log4j 1.x is EOL with known CVEs; SLF4J is required for Spring Boot 3 and modern stacks",
                    fp.evidence().stream().filter(e -> e.contains("log4j")).limit(5).toList(),
                    StepMode.AUTOMATED));
        }

        if (fp.frameworks().stream().anyMatch(f -> f.startsWith("STRUTS"))) {
            steps.add(step(
                    "struts-form-beans",
                    "framework",
                    "Scaffold typed form POJOs from Struts form-bean definitions",
                    "upgrd:StrutsFormBeanScaffold",
                    "Spring MVC @ModelAttribute binding requires typed form classes; UpGrd generates POJOs from struts-config.xml and validation.xml",
                    fp.evidence().stream().filter(e -> e.contains("struts-config")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "struts-to-spring-mvc",
                    "framework",
                    "Migrate Struts actions to Spring MVC controllers",
                    "upgrd:StrutsActionToSpringController",
                    "Struts is unmaintained; unify on Spring MVC for a single web framework on Java " + targetJava,
                    fp.evidence().stream().filter(e -> e.contains("Struts")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "struts-form-binding",
                    "framework",
                    "Scaffold @ControllerAdvice for Struts ActionForm → typed form binding",
                    "upgrd:StrutsFormBindingAdvice",
                    "Spring MVC needs @ModelAttribute/@InitBinder stubs when replacing Struts ActionForm session/request scope",
                    fp.evidence().stream().filter(e -> e.contains("struts-config")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "struts-config-to-spring",
                    "framework",
                    "Convert struts-config.xml action mappings to Spring MVC hints",
                    "upgrd:StrutsConfigToSpring",
                    "Struts URL mappings must be recreated in Spring MVC; UpGrd generates a starter config from struts-config.xml",
                    fp.evidence().stream().filter(e -> e.contains("struts-config")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "struts-view-to-spring",
                    "framework",
                    "Generate Struts JSP and validation.xml → Spring MVC hints",
                    "upgrd:StrutsViewToSpringHints",
                    "View layer and form validation require manual Spring MVC migration; UpGrd documents tag and rule mappings",
                    fp.evidence().stream().filter(e -> e.contains("Struts") || e.contains(".jsp")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "struts-jsp-to-thymeleaf",
                    "framework",
                    "Scaffold Thymeleaf templates from Struts JSP views",
                    "upgrd:StrutsJspToThymeleaf",
                    "Thymeleaf is the recommended Spring 6 view layer; UpGrd generates starter templates from Struts JSPs",
                    fp.evidence().stream().filter(e -> e.contains(".jsp")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "thymeleaf-wiring",
                    "framework",
                    "Wire Thymeleaf view resolver and validation dependencies",
                    "upgrd:ThymeleafWiring",
                    "Scaffolded Thymeleaf HTML requires Spring MVC ViewResolver and jakarta.validation on the classpath",
                    List.of("templates/"),
                    StepMode.AUTOMATED));
        }

        if (fp.frameworks().stream().anyMatch(f -> f.startsWith("SPRING_MVC"))) {
            steps.add(step(
                    "spring-4-to-6",
                    "framework",
                    "Upgrade Spring MVC 4.x to Spring 6 / Boot 3 baseline",
                    "upgrd:Spring4To6",
                    "Spring 4.x is incompatible with Jakarta EE and Java 21 without upgrading to Spring 6",
                    fp.evidence().stream().filter(e -> e.contains("spring")).limit(5).toList(),
                    StepMode.AUTOMATED));
        }
    }

    private void addSecurityRemediationSteps(List<UpgradeStep> steps, SecurityReport security) {
        if (security == null || security.findings().isEmpty()) {
            return;
        }
        for (SecurityFinding finding : security.findings()) {
            if (!finding.autoFixable() || finding.recipeId() == null) {
                continue;
            }
            String stepId = stepIdForRecipe(finding.recipeId());
            if (steps.stream().anyMatch(s -> s.id().equals(stepId))) {
                continue;
            }
            steps.add(step(
                    stepId,
                    "security",
                    "Remediate: " + finding.description(),
                    finding.recipeId(),
                    finding.remediation() + (finding.cveId() != null ? " (" + finding.cveId() + ")" : ""),
                    List.of(finding.file()),
                    StepMode.AUTOMATED));
        }
    }

    private String stepIdForRecipe(String recipeId) {
        return switch (recipeId) {
            case "upgrd:Log4j1ToSlf4j" -> "migrate-log4j1";
            case "upgrd:RemediateWeakHash" -> "remediate-weak-crypto";
            case "upgrd:ExternalizeSecrets" -> "remediate-secrets";
            case "upgrd:RemediateSqlConcatenation" -> "remediate-sql-concatenation";
            case "upgrd:RemediateDeserialization" -> "remediate-deserialization";
            default -> "remediate-" + recipeId.replace("upgrd:", "").toLowerCase();
        };
    }

    private void addLegacyBackendSteps(List<UpgradeStep> steps, TechnologyFingerprint fp) {
        if (fp.riskSignals().contains("raw-collections") || fp.riskSignals().contains("legacy-collections")) {
            steps.add(step(
                    "replace-raw-collections",
                    "typing",
                    "Replace raw and legacy collection types",
                    "upgrd:ReplaceRawCollections",
                    "Parameterized collections improve type safety and enable safer automated refactors",
                    fp.evidence().stream()
                            .filter(e -> e.contains("raw") || e.contains("Vector"))
                            .limit(5)
                            .toList(),
                    StepMode.AUTOMATED));
        }

        steps.add(step(
                "introduce-layering",
                "architecture",
                "Propose service/repository layer extraction",
                "upgrd:ExtractServiceLayer",
                "Backend code without clear layering is harder to test; UpGrd surfaces candidates for manual review",
                fp.riskSignals(),
                StepMode.ADVISORY));

        steps.add(step(
                "add-interfaces",
                "architecture",
                "Suggest abstractions for tightly coupled classes",
                "upgrd:SuggestAbstractions",
                "Introducing interfaces improves testability; advisory-only to avoid silent structural changes",
                fp.riskSignals(),
                StepMode.ADVISORY));
    }

    private UpgradeStep step(
            String id,
            String category,
            String description,
            String recipe,
            String reason,
            List<String> evidence,
            StepMode mode) {
        return new UpgradeStep(id, category, description, recipe, reason, evidence, mode, classify(id, mode));
    }

    private ChangeClassification classify(String stepId, StepMode mode) {
        if (mode == StepMode.ADVISORY) {
            return ChangeClassification.REWRITE_REQUIRED;
        }
        return switch (stepId) {
            case "convert-maven", "upgrade-java", "migrate-log4j1", "portable-jakarta",
                    "remediate-weak-crypto", "remediate-secrets", "war-authoritative-merge" -> ChangeClassification.MANDATORY;
            case "openrewrite-dry-run", "openrewrite-apply", "openrewrite-sql-scan",
                    "wildfly-local", "weblogic-adapters", "security-verify",
                    "test-scaffold", "automation-ready", "openrewrite-scaffold",
                    "war-source-sync", "war-lib-align", "api-manual-rewrite" -> ChangeClassification.OPTIONAL;
            default -> ChangeClassification.RECOMMENDED;
        };
    }

    public Path writePlan(UpgradePlan plan, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path planFile = outputDir.resolve("upgrade-plan.json");
        mapper.writeValue(planFile.toFile(), plan);
        return planFile;
    }
}
