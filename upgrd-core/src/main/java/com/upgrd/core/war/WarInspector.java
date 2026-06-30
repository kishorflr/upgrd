package com.upgrd.core.war;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public String classEntryPath(String qualifiedName) {
        return "WEB-INF/classes/" + qualifiedName.replace('.', '/') + ".class";
    }

    public String libEntryPath(String jarName) {
        return "WEB-INF/lib/" + jarName;
    }

    public boolean extractClass(Path warFile, String qualifiedName, Path destination) throws IOException {
        String entry = classEntryPath(qualifiedName);
        try (ZipFile zip = new ZipFile(warFile.toFile())) {
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                return false;
            }
            Files.createDirectories(destination.getParent());
            try (var in = zip.getInputStream(zipEntry)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }

    public boolean extractJar(Path warFile, String jarName, Path destination) throws IOException {
        String entry = libEntryPath(jarName);
        try (ZipFile zip = new ZipFile(warFile.toFile())) {
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                return false;
            }
            Files.createDirectories(destination.getParent());
            try (var in = zip.getInputStream(zipEntry)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }
}
