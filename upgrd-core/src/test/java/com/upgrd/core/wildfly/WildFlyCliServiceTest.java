package com.upgrd.core.wildfly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildFlyCliServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void statusReportsMissingScaffold() throws Exception {
        var status = new WildFlyCliService().status(tempDir.resolve("upgrd-out"));
        assertFalse(status.scaffoldPresent());
        assertTrue(status.notes().stream().anyMatch(n -> n.contains("Scaffold")));
    }

    @Test
    void statusReportsPresentScaffold() throws Exception {
        Path migrated = tempDir.resolve("upgrd-out/migrated/deploy/wildfly");
        Files.createDirectories(migrated);
        Files.writeString(migrated.resolve("docker-compose.yml"), "services: {}");

        var status = new WildFlyCliService().status(tempDir.resolve("upgrd-out"));
        assertTrue(status.scaffoldPresent());
    }
}
