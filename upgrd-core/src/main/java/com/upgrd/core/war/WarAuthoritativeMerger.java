package com.upgrd.core.war;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.WarConflictPolicy;
import com.upgrd.core.model.WarMergeConflict;
import com.upgrd.core.model.WarMergeReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Merges production WAR truth into migrated app-web layout (classes, libs, conflict markers).
 */
public final class WarAuthoritativeMerger {

    private final WarInspector warInspector = new WarInspector();
    private final WarJavaStubGenerator stubGenerator = new WarJavaStubGenerator();

    public WarMergeReport merge(
            Path warFile,
            Path appWebRoot,
            Path sourceRoot,
            SyncReport sync,
            WarConflictPolicy policy) throws IOException {
        if (sync == null || !warInspector.isWar(warFile)) {
            throw new IOException("WAR merge requires a valid WAR and sync report from analyze");
        }

        Path webInfClasses = appWebRoot.resolve("src/main/webapp/WEB-INF/classes");
        Path webInfLib = appWebRoot.resolve("src/main/webapp/WEB-INF/lib");
        Path stubRoot = appWebRoot.resolve(".upgrd/war-stubs");
        Path conflictDir = appWebRoot.resolve(".upgrd");
        Files.createDirectories(webInfClasses);
        Files.createDirectories(webInfLib);
        Files.createDirectories(stubRoot);

        List<String> mergedLibs = new ArrayList<>();
        List<String> extractedClasses = new ArrayList<>();
        List<String> generatedStubs = new ArrayList<>();
        List<WarMergeConflict> conflicts = new ArrayList<>();

        for (String jar : sync.onlyInWarLibs()) {
            Path dest = webInfLib.resolve(jar);
            if (warInspector.extractJar(warFile, jar, dest)) {
                mergedLibs.add("WEB-INF/lib/" + jar);
            }
        }

        for (String className : sync.onlyInWar()) {
            Path classDest = webInfClasses.resolve(className.replace('.', '/') + ".class");
            if (warInspector.extractClass(warFile, className, classDest)) {
                extractedClasses.add(warInspector.classEntryPath(className));
            }
            Path stub = stubGenerator.generateStub(stubRoot, className, warInspector.classEntryPath(className), policy);
            generatedStubs.add(appWebRoot.relativize(stub).toString().replace('\\', '/'));
        }

        for (String className : sync.inBoth()) {
            Optional<Path> sourceJava = findSourceJava(sourceRoot, className);
            if (sourceJava.isEmpty()) {
                continue;
            }
            switch (policy) {
                case WAR_WINS -> {
                    Path classDest = webInfClasses.resolve(className.replace('.', '/') + ".class");
                    warInspector.extractClass(warFile, className, classDest);
                    extractedClasses.add(warInspector.classEntryPath(className) + " (war-wins-over-source)");
                }
                case MARK_CONFLICT -> conflicts.add(new WarMergeConflict(
                        className,
                        sourceRoot.relativize(sourceJava.get()).toString().replace('\\', '/'),
                        warInspector.classEntryPath(className),
                        policy,
                        "Source .java and production .class differ — review before upgrade recipes run"));
                case SOURCE_WINS -> {
                    // keep source; record skip
                }
            }
        }

        if (!conflicts.isEmpty()) {
            writeConflictMarkers(conflictDir, sourceRoot, conflicts);
        }

        return new WarMergeReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                warFile.toAbsolutePath().normalize().toString(),
                policy,
                mergedLibs.size(),
                extractedClasses.size(),
                generatedStubs.size(),
                conflicts.size(),
                List.copyOf(mergedLibs),
                List.copyOf(extractedClasses),
                List.copyOf(generatedStubs),
                List.copyOf(conflicts));
    }

    private void writeConflictMarkers(
            Path conflictDir,
            Path sourceRoot,
            List<WarMergeConflict> conflicts) throws IOException {
        Path markerFile = conflictDir.resolve("war-conflicts.json");
        Files.createDirectories(conflictDir);
        new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(markerFile.toFile(), conflicts);
        for (WarMergeConflict conflict : conflicts) {
            Path sourceFile = sourceRoot.resolve(conflict.sourceFile());
            if (Files.isRegularFile(sourceFile)) {
                Path sidecar = sourceFile.resolveSibling(sourceFile.getFileName() + ".upgrd-war-conflict");
                Files.writeString(sidecar, "WAR conflict: " + conflict.resolution() + System.lineSeparator());
            }
        }
    }

    private Optional<Path> findSourceJava(Path sourceRoot, String qualifiedName) throws IOException {
        String relative = qualifiedName.replace('.', '/') + ".java";
        List<Path> roots = List.of(
                sourceRoot.resolve("src/main/java"),
                sourceRoot.resolve("src"),
                sourceRoot);
        for (Path root : roots) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(relative.substring(relative.lastIndexOf('/') + 1)))
                    .filter(p -> {
                        String pathStr = sourceRoot.relativize(p).toString().replace('\\', '/');
                        return pathStr.endsWith(relative);
                    })
                    .findFirst();
        }
    }
}
