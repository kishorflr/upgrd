package com.upgrd.core.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildFlySmokeCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void checkReportsMissingScaffold() throws Exception {
        Path output = tempDir.resolve("upgrd-out");
        Files.createDirectories(output);

        var result = new WildFlySmokeChecker().check(output);
        assertFalse(result.scaffoldPresent());
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("Scaffold")));
    }

    @Test
    void checkReportsPresentScaffold() throws Exception {
        Path output = tempDir.resolve("upgrd-out");
        Path wildfly = output.resolve("migrated/deploy/wildfly");
        Files.createDirectories(wildfly);
        Files.writeString(wildfly.resolve("docker-compose.yml"), "services: {}");

        var result = new WildFlySmokeChecker().check(output);
        assertTrue(result.scaffoldPresent());
    }
}
