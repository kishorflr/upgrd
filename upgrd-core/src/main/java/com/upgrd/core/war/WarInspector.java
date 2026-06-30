package com.upgrd.core.war;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class WarInspector {

    public Set<String> listApplicationClasses(Path warFile) throws IOException {
        Set<String> classes = new TreeSet<>();
        try (ZipFile zip = new ZipFile(warFile.toFile())) {
            zip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("WEB-INF/classes/"))
                    .filter(name -> name.endsWith(".class"))
                    .filter(name -> !name.contains("$")) // skip inner classes for v1
                    .map(this::toQualifiedName)
                    .forEach(classes::add);
        }
        return classes;
    }

    public Set<String> listLibraryJars(Path warFile) throws IOException {
        Set<String> jars = new TreeSet<>();
        try (ZipFile zip = new ZipFile(warFile.toFile())) {
            zip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("WEB-INF/lib/"))
                    .filter(name -> name.endsWith(".jar"))
                    .map(name -> name.substring("WEB-INF/lib/".length()))
                    .forEach(jars::add);
        }
        return jars;
    }

    private String toQualifiedName(String entryName) {
        String relative = entryName.substring("WEB-INF/classes/".length(), entryName.length() - ".class".length());
        return relative.replace('/', '.');
    }

    public boolean isWar(Path path) {
        if (path == null) {
            return false;
        }
        if (!path.toString().endsWith(".war") || !java.nio.file.Files.isRegularFile(path)) {
            return false;
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.getEntry("WEB-INF/") != null;
        } catch (IOException ex) {
            return false;
        }
    }
}
