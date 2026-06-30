package com.upgrd.core.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class SourceInspector {

    public Set<String> listSourceClasses(Path sourceRoot, Iterable<String> sourceRoots) throws IOException {
        Set<String> classes = new TreeSet<>();
        for (String relativeRoot : sourceRoots) {
            Path javaRoot = sourceRoot.resolve(relativeRoot);
            if (!Files.isDirectory(javaRoot)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(javaRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .map(path -> toQualifiedName(javaRoot, path))
                        .forEach(classes::add);
            }
        }
        return classes;
    }

    public Set<String> listLibraryJars(Path sourceRoot) throws IOException {
        Set<String> jars = new TreeSet<>();
        for (Path lib : List.of(
                sourceRoot.resolve("lib"),
                sourceRoot.resolve("WEB-INF/lib"),
                sourceRoot.resolve("WebContent/WEB-INF/lib"))) {
            if (!Files.isDirectory(lib)) {
                continue;
            }
            try (Stream<Path> list = Files.list(lib)) {
                list.filter(path -> path.toString().endsWith(".jar"))
                        .map(path -> path.getFileName().toString())
                        .forEach(jars::add);
            }
        }
        return jars;
    }

    private String toQualifiedName(Path javaRoot, Path javaFile) {
        Path relative = javaRoot.relativize(javaFile);
        String withoutExtension = relative.toString().replace('\\', '/');
        if (withoutExtension.endsWith(".java")) {
            withoutExtension = withoutExtension.substring(0, withoutExtension.length() - 5);
        }
        return withoutExtension.replace('/', '.');
    }
}
