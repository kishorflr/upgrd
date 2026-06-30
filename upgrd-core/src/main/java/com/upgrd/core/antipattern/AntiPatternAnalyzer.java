package com.upgrd.core.antipattern;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AntiPatternFinding;
import com.upgrd.core.model.AntiPatternReport;
import com.upgrd.core.model.ProjectProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Rule-based anti-pattern detection for compliance and refactoring prioritization.
 */
public final class AntiPatternAnalyzer {

    private final List<AntiPatternRule> rules = AntiPatternRuleCatalog.rules();

    public AntiPatternReport analyze(
            Path sourceRoot,
            List<String> sourceRoots,
            ProjectProfile profile) throws IOException {
        List<AntiPatternFinding> findings = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();

        for (String sourceRootRel : sourceRoots) {
            Path root = sourceRoot.resolve(sourceRootRel);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(file -> scan(file, sourceRoot, findings, counter));
            }
        }

        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        for (AntiPatternFinding finding : findings) {
            bySeverity.merge(finding.severity(), 1, Integer::sum);
        }

        return new AntiPatternReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                profile,
                findings.size(),
                Map.copyOf(bySeverity),
                List.copyOf(findings));
    }

    private void scan(Path file, Path sourceRoot, List<AntiPatternFinding> findings, AtomicInteger counter) {
        try {
            String content = Files.readString(file);
            String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
            AntiPatternRule.IdGenerator ids = ruleId -> ruleId + "-" + String.format("%04d", counter.incrementAndGet());
            for (AntiPatternRule rule : rules) {
                findings.addAll(rule.detect(relative, content, ids));
            }
        } catch (IOException ignored) {
            // skip unreadable files
        }
    }
}
