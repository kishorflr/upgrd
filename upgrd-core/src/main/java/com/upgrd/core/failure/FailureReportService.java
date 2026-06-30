package com.upgrd.core.failure;

import com.upgrd.core.model.AnonymousFailureReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Orchestrates capture, sanitization, and persistence of anonymous failure reports.
 */
public final class FailureReportService {

    private final AnonymousFailureSanitizer sanitizer = new AnonymousFailureSanitizer();
    private final FailureReportWriter writer = new FailureReportWriter();

    public List<String> generateFromLog(
            Path logFile,
            Path reportDir,
            String failureKind,
            Path migratedRoot) throws IOException {
        String raw = Files.readString(logFile);
        return generateFromText(raw, reportDir, failureKind, migratedRoot);
    }

    public List<String> generateFromText(
            String rawLog,
            Path reportDir,
            String failureKind,
            Path migratedRoot) throws IOException {
        List<String> appPrefixes = discoverAppPackages(migratedRoot);
        Map<String, String> environment = Map.of(
                "java.version", System.getProperty("java.version", "unknown"),
                "os.name", System.getProperty("os.name", "unknown"));
        AnonymousFailureReport report = sanitizer.sanitize(rawLog, failureKind, appPrefixes, environment);
        return writer.write(report, reportDir);
    }

    private List<String> discoverAppPackages(Path migratedRoot) throws IOException {
        Set<String> prefixes = new LinkedHashSet<>();
        Path mainJava = migratedRoot.resolve("app-web/src/main/java");
        if (!Files.isDirectory(mainJava)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(mainJava)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            String relative = mainJava.relativize(file).toString().replace('\\', '/');
                            String pkg = relative.replace(".java", "").replace('/', '.');
                            int lastDot = pkg.lastIndexOf('.');
                            if (lastDot > 0) {
                                prefixes.add(pkg.substring(0, lastDot));
                            }
                        } catch (Exception ignored) {
                            // skip unreadable files
                        }
                    });
        }
        return new ArrayList<>(prefixes);
    }
}
