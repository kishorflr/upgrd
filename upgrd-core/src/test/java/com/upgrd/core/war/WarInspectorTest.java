package com.upgrd.core.war;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarInspectorTest {

    @TempDir
    Path tempDir;

    private final WarInspector inspector = new WarInspector();

    @Test
    void listsClassesAndLibsFromWar() throws Exception {
        Path war = tempDir.resolve("app.war");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(war))) {
            zos.putNextEntry(new ZipEntry("WEB-INF/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("WEB-INF/classes/com/example/App.class"));
            zos.write(new byte[] {0});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("WEB-INF/lib/log4j-1.2.17.jar"));
            zos.write(new byte[] {0});
            zos.closeEntry();
        }

        assertTrue(inspector.isWar(war));
        Set<String> classes = inspector.listApplicationClasses(war);
        assertEquals(1, classes.size());
        assertTrue(classes.contains("com.example.App"));

        Set<String> libs = inspector.listLibraryJars(war);
        assertEquals(1, libs.size());
        assertTrue(libs.contains("log4j-1.2.17.jar"));
    }
}
