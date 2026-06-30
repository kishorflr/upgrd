package com.upgrd.core.logs;

import com.upgrd.core.model.LogKind;
import com.upgrd.core.model.LogSourceEntry;
import com.upgrd.core.model.LogSourceManifest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Resolves plain, .gz, and .zip log archives into a staging directory with collision-safe names.
 * Duplicate basenames (e.g. access.log from archives 1..10) become access__001.log, access__002.log, ...
 */
public final class LogArchiveResolver {

    private static final List<String> LOG_EXTENSIONS = List.of(".log", ".txt", ".out");

    public LogSourceManifest resolve(Path logsDir, Path stagingDir) throws IOException {
        if (!Files.isDirectory(logsDir)) {
            throw new IOException("Logs directory not found: " + logsDir);
        }
        Files.createDirectories(stagingDir);
        clearStaging(stagingDir);

        List<Path> candidates = new ArrayList<>();
        Files.walkFileTree(logsDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isLogCandidate(file)) {
                    candidates.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        candidates.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));

        Map<String, Integer> globalCounters = new HashMap<>();
        List<LogSourceEntry> entries = new ArrayList<>();
        int archiveCount = 0;
        int plainCount = 0;

        for (Path candidate : candidates) {
            String name = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".zip")) {
                archiveCount++;
                entries.addAll(extractZip(candidate, logsDir, stagingDir, globalCounters));
            } else if (name.endsWith(".gz")) {
                archiveCount++;
                entries.add(extractGzip(candidate, logsDir, stagingDir, globalCounters));
            } else {
                plainCount++;
                entries.add(stagePlain(candidate, logsDir, stagingDir, globalCounters));
            }
        }

        return new LogSourceManifest(
                stagingDir.toAbsolutePath().normalize().toString(),
                Instant.now(),
                archiveCount,
                entries.size() - plainCount,
                plainCount,
                List.copyOf(entries));
    }

    public List<Path> stagedPaths(LogSourceManifest manifest) {
        return manifest.entries().stream()
                .map(entry -> Path.of(manifest.stagingDir(), entry.stagedFile()))
                .toList();
    }

    public static LogKind detectKind(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("access")) {
            return LogKind.ACCESS;
        }
        if (lower.contains("server") || lower.contains("catalina")) {
            return LogKind.SERVER;
        }
        if (lower.contains("out") || lower.contains("stdout") || lower.contains("stderr")) {
            return LogKind.OUT;
        }
        if (lower.contains("application") || lower.contains("app.log") || lower.contains("app-")) {
            return LogKind.APPLICATION;
        }
        return LogKind.UNKNOWN;
    }

    private boolean isLogCandidate(Path file) {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip") || lower.endsWith(".gz")) {
            return true;
        }
        for (String ext : LOG_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private List<LogSourceEntry> extractZip(
            Path zipFile,
            Path logsRoot,
            Path stagingDir,
            Map<String, Integer> globalCounters) throws IOException {
        List<LogSourceEntry> entries = new ArrayList<>();
        String archiveLabel = sanitize(stripExtension(zipFile.getFileName().toString()));
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = Path.of(entry.getName()).getFileName().toString();
                if (!isLogLikeName(entryName)) {
                    continue;
                }
                String stem = sanitize(stripExtension(entryName));
                String stagedName = uniqueName(stem + "__" + archiveLabel, globalCounters) + logExtension(entryName);
                Path dest = stagingDir.resolve(stagedName);
                Files.createDirectories(dest.getParent());
                copyStream(zis, dest);
                entries.add(new LogSourceEntry(
                        stagedName,
                        entry.getName(),
                        relativize(logsRoot, zipFile),
                        detectKind(entryName),
                        globalCounters.get(stem + "__" + archiveLabel)));
            }
        }
        return entries;
    }

    private LogSourceEntry extractGzip(
            Path gzipFile,
            Path logsRoot,
            Path stagingDir,
            Map<String, Integer> globalCounters) throws IOException {
        String fileName = gzipFile.getFileName().toString();
        String innerName = fileName.endsWith(".gz")
                ? fileName.substring(0, fileName.length() - 3)
                : fileName + ".log";
        String stem = sanitize(stripExtension(innerName));
        String archiveLabel = sanitize(stripExtension(gzipFile.getParent() != null
                ? gzipFile.getParent().getFileName().toString()
                : "archive"));
        String key = stem + "__" + archiveLabel;
        String stagedName = uniqueName(key, globalCounters) + logExtension(innerName);
        Path dest = stagingDir.resolve(stagedName);
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gzipFile));
             OutputStream out = Files.newOutputStream(dest)) {
            in.transferTo(out);
        }
        return new LogSourceEntry(
                stagedName,
                innerName,
                relativize(logsRoot, gzipFile),
                detectKind(innerName),
                globalCounters.get(key));
    }

    private LogSourceEntry stagePlain(
            Path logFile,
            Path logsRoot,
            Path stagingDir,
            Map<String, Integer> globalCounters) throws IOException {
        String fileName = logFile.getFileName().toString();
        String stem = sanitize(stripExtension(fileName));
        String stagedName = uniqueName(stem, globalCounters) + logExtension(fileName);
        Path dest = stagingDir.resolve(stagedName);
        Files.copy(logFile, dest, StandardCopyOption.REPLACE_EXISTING);
        return new LogSourceEntry(
                stagedName,
                fileName,
                relativize(logsRoot, logFile),
                detectKind(fileName),
                globalCounters.get(stem));
    }

    private String uniqueName(String stem, Map<String, Integer> counters) {
        int next = counters.merge(stem, 1, Integer::sum);
        return stem + "__" + String.format("%03d", next);
    }

    private String logExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String ext = name.substring(dot).toLowerCase(Locale.ROOT);
            if (LOG_EXTENSIONS.contains(ext) || ext.equals(".txt")) {
                return ext;
            }
        }
        return ".log";
    }

    private boolean isLogLikeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip") || lower.endsWith(".gz") || lower.endsWith(".jar")) {
            return false;
        }
        for (String ext : LOG_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return lower.contains("log") || lower.contains("out");
    }

    private void clearStaging(Path stagingDir) throws IOException {
        if (!Files.isDirectory(stagingDir)) {
            return;
        }
        try (var walk = Files.walk(stagingDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    if (!path.equals(stagingDir)) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to clear staging file " + path, ex);
                }
            });
        }
    }

    private void copyStream(InputStream in, Path dest) throws IOException {
        try (OutputStream out = Files.newOutputStream(dest)) {
            in.transferTo(out);
        }
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private String relativize(Path root, Path file) {
        try {
            return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
        } catch (Exception ex) {
            return file.getFileName().toString();
        }
    }
}
