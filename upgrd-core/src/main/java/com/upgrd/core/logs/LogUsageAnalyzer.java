package com.upgrd.core.logs;

import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogUsageAnalyzer {

    private static final Pattern STACK_TRACE = Pattern.compile("\\bat\\s+([a-zA-Z_][\\w.$]*\\.[a-zA-Z_][\\w$]*)\\.");
    private static final Pattern LOGGER_PREFIX = Pattern.compile("\\b([a-z][\\w.]*)\\s+-\\s+");
    private static final Pattern SERVLET_PATH = Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH)\\s+(/\\S*)");

    public UsageReport analyze(List<Path> logFiles, Set<String> warClasses) throws IOException {
        Map<String, Integer> hits = new HashMap<>();
        Map<String, String> samples = new HashMap<>();

        for (Path logFile : logFiles) {
            if (!Files.isRegularFile(logFile)) {
                continue;
            }
            for (String line : Files.readAllLines(logFile)) {
                recordMatch(hits, samples, STACK_TRACE, line, 1);
                recordMatch(hits, samples, LOGGER_PREFIX, line, 1);
                recordServletPath(hits, samples, line);
                recordWarClassMentions(hits, samples, line, warClasses);
            }
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
        return new UsageReport(logFiles.size(), totalHits, rankedHits, unused);
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
            String key = "path:" + matcher.group(2);
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
