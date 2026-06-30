package com.upgrd.core.design;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.DesignAdvisory;
import com.upgrd.core.model.DesignAdvisoryReport;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.TechnologyFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Surfaces structural design smells as advisory recommendations (not auto-applied).
 */
public final class DesignAdvisoryAnalyzer {

    private static final int GOD_CLASS_LINES = 500;
    private static final int LARGE_METHOD_THRESHOLD = 80;

    public DesignAdvisoryReport analyze(
            Path sourceRoot,
            List<String> sourceRoots,
            ProjectProfile profile,
            TechnologyFingerprint fingerprint) throws IOException {
        List<DesignAdvisory> advisories = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();

        for (String sourceRootRel : sourceRoots) {
            Path javaRoot = sourceRoot.resolve(sourceRootRel);
            if (!Files.isDirectory(javaRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(javaRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> scanJavaFile(path, sourceRoot, advisories, counter));
            }
        }

        addProfileAdvisories(profile, fingerprint, advisories, counter);

        return new DesignAdvisoryReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                profile,
                List.copyOf(advisories));
    }

    private void scanJavaFile(
            Path file,
            Path sourceRoot,
            List<DesignAdvisory> advisories,
            AtomicInteger counter) {
        try {
            String content = Files.readString(file);
            String rel = sourceRoot.relativize(file).toString();
            List<String> lines = content.lines().toList();

            if (lines.size() > GOD_CLASS_LINES) {
                advisories.add(new DesignAdvisory(
                        id(counter, "god-class"),
                        "structure",
                        rel,
                        List.of(1, lines.size()),
                        "god-class",
                        "Split into focused classes by responsibility (service, repository, DTO)",
                        "Class exceeds " + GOD_CLASS_LINES + " lines; harder to test and maintain after Java 21 upgrade",
                        List.of(rel + ": " + lines.size() + " lines"),
                        "MEDIUM"));
            }

            if (content.contains("@SuppressWarnings(\"rawtypes\")")
                    || (content.contains("List ") && !content.contains("List<"))) {
                advisories.add(new DesignAdvisory(
                        id(counter, "raw-types"),
                        "typing",
                        rel,
                        List.of(1, Math.min(lines.size(), 20)),
                        "raw-collections",
                        "Replace raw types with parameterized generics",
                        "Raw collections lose type safety and block modern API migration tooling",
                        List.of(rel + ": raw type usage detected"),
                        "LOW"));
            }

            if (content.contains("Vector") || content.contains("Hashtable")) {
                advisories.add(new DesignAdvisory(
                        id(counter, "legacy-collections"),
                        "typing",
                        rel,
                        List.of(1, Math.min(lines.size(), 20)),
                        "legacy-collections",
                        "Replace Vector/Hashtable with ArrayList/ConcurrentHashMap",
                        "Legacy synchronized collections are slower and discourage modern concurrency patterns",
                        List.of(rel + ": Vector/Hashtable"),
                        "LOW"));
            }

            if (content.contains("static ") && content.contains(" = new ") && !content.contains("final")) {
                advisories.add(new DesignAdvisory(
                        id(counter, "mutable-static"),
                        "concurrency",
                        rel,
                        List.of(1, Math.min(lines.size(), 30)),
                        "mutable-static-state",
                        "Extract to instance fields with dependency injection or make field final",
                        "Mutable static state complicates testing and is a common source of thread-safety bugs",
                        List.of(rel + ": non-final static mutable field"),
                        "HIGH"));
            }

            detectLargeMethods(content, rel, lines, advisories, counter);
        } catch (IOException ignored) {
            // skip unreadable files
        }
    }

    private void detectLargeMethods(
            String content,
            String rel,
            List<String> lines,
            List<DesignAdvisory> advisories,
            AtomicInteger counter) {
        int braceDepth = 0;
        int methodStart = -1;
        String methodName = "unknown";

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (braceDepth == 0 && line.matches("^(public|protected|private|static|\\s)+[\\w<>,\\[\\]\\s]+\\s+\\w+\\s*\\(.*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\s*\\{?\\s*$")) {
                methodStart = i + 1;
                int paren = line.indexOf('(');
                int space = line.lastIndexOf(' ', paren);
                methodName = space >= 0 ? line.substring(space + 1, paren).trim() : "method";
            }
            braceDepth += countChar(line, '{') - countChar(line, '}');
            if (methodStart > 0 && braceDepth == 0) {
                int methodLines = i - methodStart + 1;
                if (methodLines > LARGE_METHOD_THRESHOLD) {
                    advisories.add(new DesignAdvisory(
                            id(counter, "long-method"),
                            "structure",
                            rel,
                            List.of(methodStart, i + 1),
                            "long-method",
                            "Extract helper methods or a dedicated service for " + methodName,
                            "Methods over " + LARGE_METHOD_THRESHOLD + " lines are hard to unit test and refactor safely",
                            List.of(rel + ": " + methodName + "() spans " + methodLines + " lines"),
                            "MEDIUM"));
                }
                methodStart = -1;
            }
        }
    }

    private void addProfileAdvisories(
            ProjectProfile profile,
            TechnologyFingerprint fingerprint,
            List<DesignAdvisory> advisories,
            AtomicInteger counter) {
        if (profile == ProjectProfile.LEGACY_BACKEND) {
            advisories.add(new DesignAdvisory(
                    id(counter, "layering"),
                    "architecture",
                    "(project)",
                    List.of(),
                    "missing-layering",
                    "Introduce service/repository layers before adding tests",
                    "Backend projects without clear layering benefit from explicit boundaries before Java 21 migration",
                    fingerprint.evidence().stream().limit(5).toList(),
                    "MEDIUM"));
        }
    }

    private int countChar(String line, char ch) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    private String id(AtomicInteger counter, String prefix) {
        return prefix + "-" + String.format("%04d", counter.incrementAndGet());
    }
}
