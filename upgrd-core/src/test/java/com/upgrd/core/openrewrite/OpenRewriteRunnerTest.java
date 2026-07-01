package com.upgrd.core.openrewrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRewriteRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void blocksApplyWhenDryRunGateMissing() throws Exception {
        Path output = tempDir.resolve("upgrd-out");
        Path migrated = output.resolve("migrated");
        Files.createDirectories(migrated);
        Files.writeString(migrated.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
                  <modules><module>app-web</module></modules>
                </project>
                """);

        var result = new OpenRewriteRunner().run(output, false, true);
        assertFalse(result.success());
        assertTrue(result.message().contains("dry-run gate"));
    }

    @Test
    @EnabledIf("com.upgrd.core.process.MavenCommand#isAvailable")
    void sqlScanRecipeSkipsDryRunGate() throws Exception {
        Path output = tempDir.resolve("upgrd-out");
        Path migrated = output.resolve("migrated");
        Files.createDirectories(migrated);
        Files.writeString(migrated.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0</version>
                  <properties><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
                </project>
                """);

        var result = new OpenRewriteRunner().run(
                output, false, true, OpenRewriteRunner.SQL_SCAN_RECIPE);
        assertTrue(result.message().contains(OpenRewriteRunner.SQL_SCAN_RECIPE));
    }
}
