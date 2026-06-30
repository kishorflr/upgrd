package com.upgrd.core.war;

import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.SyncSeverity;
import com.upgrd.core.model.WarConflictPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarAuthoritativeMergerTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesWarOnlyClassesAndLibs() throws Exception {
        Path war = tempDir.resolve("prod.war");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(war))) {
            zos.putNextEntry(new ZipEntry("WEB-INF/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("WEB-INF/classes/com/prod/OnlyInWar.class"));
            zos.write(new byte[] {0});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("WEB-INF/lib/extra.jar"));
            zos.write(new byte[] {0});
            zos.closeEntry();
        }

        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("src/Shared.java"), "public class Shared {}");

        Path appWeb = tempDir.resolve("migrated/app-web");
        Files.createDirectories(appWeb.resolve("src/main/java"));

        SyncReport sync = new SyncReport(
                2, 1,
                List.of("com.prod.OnlyInWar"),
                List.of(),
                List.of("com.example.Shared"),
                1, 0,
                List.of("extra.jar"),
                List.of(),
                List.of(),
                SyncSeverity.HIGH,
                "drift");

        var report = new WarAuthoritativeMerger().merge(
                war, appWeb, source, sync, WarConflictPolicy.WAR_WINS);

        assertEquals(1, report.mergedLibCount());
        assertEquals(1, report.extractedClassCount());
        assertTrue(Files.isRegularFile(
                appWeb.resolve("src/main/webapp/WEB-INF/classes/com/prod/OnlyInWar.class")));
        assertTrue(Files.isRegularFile(
                appWeb.resolve("src/main/webapp/WEB-INF/lib/extra.jar")));
        assertTrue(Files.isRegularFile(
                appWeb.resolve(".upgrd/war-stubs/com/prod/OnlyInWar.java")));
    }
}
