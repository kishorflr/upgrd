package com.upgrd.core.logs;

import com.upgrd.core.model.BrokenAccessSignal;
import com.upgrd.core.model.LogKind;
import com.upgrd.core.model.LogSourceEntry;
import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageReport;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogUsageAnalyzer {

    private static final Pattern STACK_TRACE = Pattern.compile(
            "\\bat\\s+([a-zA-Z_][\\w.$]*\\.[a-zA-Z_][\\w$]*)\\.");
    private static final Pattern LOGGER_PREFIX = Pattern.compile("\\b([a-z][\\w.]*)\\s+-\\s+");
    private static final Pattern SERVLET_PATH = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\s+(/\\S*)");
    private static final Pattern ACCESS_STATUS = Pattern.compile(
            "\\b(GET|POST|PUT|DELETE|PATCH)\\s+(/\\S*)\\s+HTTP/\\S+\"?\\s+(\\d{3})");
    private static final Pattern QUOTED_ACCESS_STATUS = Pattern.compile(
            "\"?(?:GET|POST|PUT|DELETE|PATCH)\\s+(/\\S*)\\S*\"?\\s+(\\d{3})");
    private static final Pattern ERROR_LINE = Pattern.compile(
            "\\b(ERROR|FATAL|SEVERE|Exception|Throwable)\\b", Pattern.CASE_INSENSITIVE);

    public UsageReport analyze(List<Path> logFiles, Set<String> warClasses) throws IOException {
        return analyze(logFiles, warClasses, List.of());
    }

    public UsageReport analyze(List<Path> logFiles, Set<String> warClasses, List<LogSourceEntry> sources)
            throws IOException {
        Map<String, Integer> hits = new HashMap<>();
        Map<String, String> samples = new HashMap<>();
        Map<String, Integer> successHits = new HashMap<>();
        Map<String, Integer> errorHits = new HashMap<>();
        Map<String, Integer> hitsByLogKind = new HashMap<>();
        Map<Path, LogKind> kindByPath = indexKinds(logFiles, sources);

        for (Path logFile : logFiles) {
            if (!Files.isRegularFile(logFile)) {
                continue;
            }
            LogKind kind = kindByPath.getOrDefault(logFile.toAbsolutePath().normalize(), detectKind(logFile));
            analyzeFile(logFile, kind, warClasses, hits, samples, successHits, errorHits, hitsByLogKind);
        }

        List<UsageHit> rankedHits = hits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new UsageHit(entry.getKey(), entry.getValue(), samples.get(entry.getKey())))
                .toList();

        List<String> unused = warClasses.stream()
                .filter(className -> !hits.containsKey(className))
                .sorted()
                .toList();

        int totalHits = hits.values().stream().mapToInt(Integer::intValue).sum();
        List<BrokenAccessSignal> brokenSignals = buildBrokenSignals(successHits, errorHits, samples, kindByPath);

        return new UsageReport(
                logFiles.size(),
                totalHits,
                rankedHits,
                unused,
                sources != null ? List.copyOf(sources) : List.of(),
                Map.copyOf(hitsByLogKind),
                brokenSignals);
    }

    private void analyzeFile(
            Path logFile,
            LogKind kind,
            Set<String> warClasses,
            Map<String, Integer> hits,
            Map<String, String> samples,
            Map<String, Integer> successHits,
            Map<String, Integer> errorHits,
            Map<String, Integer> hitsByLogKind) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                hitsByLogKind.merge(kind.name(), 1, Integer::sum);
                recordMatch(hits, samples, STACK_TRACE, line, 1);
                recordMatch(hits, samples, LOGGER_PREFIX, line, 1);
                recordServletPath(hits, samples, line);
                recordWarClassMentions(hits, samples, line, warClasses);
                recordAccessStatus(kind, line, successHits, errorHits, samples);
                recordErrorSignals(kind, line, warClasses, errorHits, samples);
            }
        }
    }

    private void recordAccessStatus(
            LogKind kind,
            String line,
            Map<String, Integer> successHits,
            Map<String, Integer> errorHits,
            Map<String, String> samples) {
        if (kind != LogKind.ACCESS && kind != LogKind.UNKNOWN) {
            return;
        }
        Matcher matcher = ACCESS_STATUS.matcher(line);
        while (matcher.find()) {
            tallyPathStatus(matcher.group(2), matcher.group(3), successHits, errorHits, samples, line);
        }
        matcher = QUOTED_ACCESS_STATUS.matcher(line);
        while (matcher.find()) {
            tallyPathStatus(matcher.group(1), matcher.group(2), successHits, errorHits, samples, line);
        }
    }

    private void tallyPathStatus(
            String path,
            String statusCode,
            Map<String, Integer> successHits,
            Map<String, Integer> errorHits,
            Map<String, String> samples,
            String line) {
        if (path == null || statusCode == null) {
            return;
        }
        String key = "path:" + normalizePath(path);
        int status = Integer.parseInt(statusCode);
        if (status >= 500 || status == 404 || status == 403) {
            errorHits.merge(key, 1, Integer::sum);
            samples.putIfAbsent("error:" + key, truncate(line));
        } else if (status >= 200 && status < 400) {
            successHits.merge(key, 1, Integer::sum);
            samples.putIfAbsent("success:" + key, truncate(line));
        }
    }

    private void recordErrorSignals(
            LogKind kind,
            String line,
            Set<String> warClasses,
            Map<String, Integer> errorHits,
            Map<String, String> samples) {
        if (kind == LogKind.ACCESS) {
            return;
        }
        if (!ERROR_LINE.matcher(line).find()) {
            return;
        }
        Matcher stack = STACK_TRACE.matcher(line);
        while (stack.find()) {
            String classKey = normalizeKey(stack.group(1));
            if (classKey != null) {
                errorHits.merge(classKey, 1, Integer::sum);
                samples.putIfAbsent("error:" + classKey, truncate(line));
            }
        }
        for (String className : warClasses) {
            if (line.contains(className)) {
                errorHits.merge(className, 1, Integer::sum);
                samples.putIfAbsent("error:" + className, truncate(line));
            }
        }
        Matcher pathMatcher = SERVLET_PATH.matcher(line);
        while (pathMatcher.find()) {
            String key = "path:" + normalizePath(pathMatcher.group(2));
            errorHits.merge(key, 1, Integer::sum);
            samples.putIfAbsent("error:" + key, truncate(line));
        }
    }

    private List<BrokenAccessSignal> buildBrokenSignals(
            Map<String, Integer> successHits,
            Map<String, Integer> errorHits,
            Map<String, String> samples,
            Map<Path, LogKind> kindByPath) {
        Set<String> keys = new HashSet<>();
        keys.addAll(successHits.keySet());
        keys.addAll(errorHits.keySet());
        List<BrokenAccessSignal> broken = new ArrayList<>();
        for (String key : keys) {
            int errors = errorHits.getOrDefault(key, 0);
            int successes = successHits.getOrDefault(key, 0);
            if (errors <= 0) {
                continue;
            }
            String sample = samples.getOrDefault("error:" + key,
                    samples.getOrDefault(key, null));
            broken.add(new BrokenAccessSignal(
                    key,
                    key.startsWith("path:") ? key.substring("path:".length()) : key,
                    errors,
                    successes,
                    sample,
                    inferBrokenKind(key)));
        }
        broken.sort(Comparator.comparingInt(BrokenAccessSignal::errorHits).reversed());
        return List.copyOf(broken);
    }

    private String inferBrokenKind(String key) {
        return key.startsWith("path:") ? LogKind.ACCESS.name() : LogKind.SERVER.name();
    }

    private Map<Path, LogKind> indexKinds(List<Path> logFiles, List<LogSourceEntry> sources) {
        Map<String, LogKind> byStagedName = new HashMap<>();
        if (sources != null) {
            for (LogSourceEntry source : sources) {
                byStagedName.put(source.stagedFile(), source.kind());
            }
        }
        Map<Path, LogKind> result = new HashMap<>();
        for (Path file : logFiles) {
            LogKind kind = byStagedName.getOrDefault(file.getFileName().toString(), detectKind(file));
            result.put(file.toAbsolutePath().normalize(), kind);
        }
        return result;
    }

    private LogKind detectKind(Path file) {
        return LogArchiveResolver.detectKind(file.getFileName().toString());
    }

    private void recordMatch(
            Map<String, Integer> hits,
            Map<String, String> samples,
            Pattern pattern,
            String line,
            int group) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String key = normalizeKey(matcher.group(group));
            if (key != null) {
                hits.merge(key, 1, Integer::sum);
                samples.putIfAbsent(key, truncate(line));
            }
        }
    }

    private void recordServletPath(Map<String, Integer> hits, Map<String, String> samples, String line) {
        Matcher matcher = SERVLET_PATH.matcher(line);
        while (matcher.find()) {
            String key = "path:" + normalizePath(matcher.group(2));
            hits.merge(key, 1, Integer::sum);
            samples.putIfAbsent(key, truncate(line));
        }
    }

    private void recordWarClassMentions(
            Map<String, Integer> hits,
            Map<String, String> samples,
            String line,
            Set<String> warClasses) {
        for (String className : warClasses) {
            if (line.contains(className)) {
                hits.merge(className, 1, Integer::sum);
                samples.putIfAbsent(className, truncate(line));
            }
        }
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.trim();
        if (normalized.contains("?")) {
            normalized = normalized.substring(0, normalized.indexOf('?'));
        }
        return normalized;
    }

    private String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("path:")) {
            return raw;
        }
        int lastDot = raw.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == raw.length() - 1) {
            return null;
        }
        return raw.substring(0, lastDot);
    }

    private String truncate(String line) {
        return line.length() <= 160 ? line : line.substring(0, 157) + "...";
    }
}
