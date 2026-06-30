package com.upgrd.core.apply;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Copies legacy source trees into the migrated Maven layout.
 */
public final class SourceMigrator {

    private static final Set<String> COPY_EXTENSIONS = Set.of(
            ".java", ".xml", ".properties", ".jsp", ".html", ".css", ".js");

    public List<String> copyToMavenLayout(Path sourceRoot, Path appWebRoot) throws IOException {
        List<String> copied = new ArrayList<>();
        Path javaDest = appWebRoot.resolve("src/main/java");
        Path webappDest = appWebRoot.resolve("src/main/webapp");
        Path resourcesDest = appWebRoot.resolve("src/main/resources");

        Files.createDirectories(javaDest);
        Files.createDirectories(webappDest);
        Files.createDirectories(resourcesDest);

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        if (name.equals("build.xml") || name.endsWith(".jar")) {
                            return;
                        }
                        if (!hasCopyExtension(name)) {
                            return;
                        }
                        try {
                            Path relative = sourceRoot.relativize(path);
                            Path dest = resolveDestination(relative, javaDest, webappDest, resourcesDest);
                            Files.createDirectories(dest.getParent());
                            Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                            copied.add(relative.toString());
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to copy " + path, ex);
                        }
                    });
        }

        copyWebInf(sourceRoot, webappDest, copied);
        return copied;
    }

    private void copyWebInf(Path sourceRoot, Path webappDest, List<String> copied) throws IOException {
        for (Path webInf : List.of(
                sourceRoot.resolve("WEB-INF"),
                sourceRoot.resolve("WebContent/WEB-INF"))) {
            if (!Files.isDirectory(webInf)) {
                continue;
            }
            Path destWebInf = webappDest.resolve("WEB-INF");
            Files.walkFileTree(webInf, new java.nio.file.SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = webInf.relativize(file);
                    Path dest = destWebInf.resolve(relative);
                    Files.createDirectories(dest.getParent());
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    copied.add("WEB-INF/" + relative);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private Path resolveDestination(Path relative, Path javaDest, Path webappDest, Path resourcesDest) {
        String pathStr = relative.toString().replace('\\', '/');
        if (pathStr.endsWith(".java")) {
            if (pathStr.startsWith("src/main/java/")) {
                return javaDest.resolve(pathStr.substring("src/main/java/".length()));
            }
            if (pathStr.startsWith("src/")) {
                return javaDest.resolve(pathStr.substring("src/".length()));
            }
            return javaDest.resolve(relative);
        }
        if (pathStr.endsWith(".jsp") || pathStr.endsWith(".html")) {
            return webappDest.resolve(relative);
        }
        if (pathStr.endsWith(".properties") || pathStr.endsWith(".xml")) {
            if (pathStr.contains("web.xml") || pathStr.contains("struts-config")) {
                return webappDest.resolve("WEB-INF").resolve(relative.getFileName());
            }
            return resourcesDest.resolve(relative.getFileName());
        }
        return resourcesDest.resolve(relative);
    }

    private boolean hasCopyExtension(String name) {
        return COPY_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
