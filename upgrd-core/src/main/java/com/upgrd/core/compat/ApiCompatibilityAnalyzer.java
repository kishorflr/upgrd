package com.upgrd.core.compat;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ApiCompatibilityHit;
import com.upgrd.core.model.ApiCompatibilityReport;
import com.upgrd.core.model.ApiRemediationType;
import com.upgrd.core.model.ProjectDiscovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Scans source against a static API compatibility catalog — unsupported, replacement, and manual paths.
 */
public final class ApiCompatibilityAnalyzer {

    private final List<ApiCatalogEntry> catalog = ApiCompatibilityCatalog.entries();

    public ApiCompatibilityReport analyze(Path sourceRoot, ProjectDiscovery discovery) throws IOException {
        List<ApiCompatibilityHit> hits = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        Map<String, Boolean> seen = new LinkedHashMap<>();

        for (String sourceRootRel : discovery.sourceRoots()) {
            Path root = sourceRoot.resolve(sourceRootRel);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(file -> scan(file, sourceRoot, hits, counter, seen));
            }
        }

        Map<ApiRemediationType, Integer> counts = new EnumMap<>(ApiRemediationType.class);
        for (ApiRemediationType type : ApiRemediationType.values()) {
            counts.put(type, 0);
        }
        for (ApiCompatibilityHit hit : hits) {
            counts.merge(hit.remediationType(), 1, Integer::sum);
        }

        return new ApiCompatibilityReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                hits.size(),
                Map.copyOf(counts),
                List.copyOf(hits));
    }

    private void scan(
            Path file,
            Path sourceRoot,
            List<ApiCompatibilityHit> hits,
            AtomicInteger counter,
            Map<String, Boolean> seen) {
        try {
            String content = Files.readString(file);
            String rel = sourceRoot.relativize(file).toString().replace('\\', '/');
            List<String> lines = content.lines().toList();

            for (ApiCatalogEntry entry : catalog) {
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!line.contains(entry.apiPattern())) {
                        continue;
                    }
                    String dedupeKey = entry.id() + "|" + rel + "|" + (i + 1);
                    if (seen.containsKey(dedupeKey)) {
                        continue;
                    }
                    seen.put(dedupeKey, true);
                    hits.add(new ApiCompatibilityHit(
                            entry.id() + "-" + String.format("%04d", counter.incrementAndGet()),
                            entry.id(),
                            entry.apiPattern(),
                            entry.remediationType(),
                            rel,
                            List.of(i + 1, i + 1),
                            truncate(line.trim()),
                            entry.replacement(),
                            entry.description(),
                            entry.recipeId(),
                            entry.planStepId()));
                }
            }
        } catch (IOException ignored) {
            // skip unreadable files
        }
    }

    private String truncate(String line) {
        if (line.length() <= 120) {
            return line;
        }
        return line.substring(0, 117) + "...";
    }
}
