package com.upgrd.core.weblogic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebLogicCliServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void statusReportsMissingScaffold() throws Exception {
        var status = new WebLogicCliService().status(tempDir.resolve("upgrd-out"));
        assertFalse(status.scaffoldPresent());
    }

    @Test
    void validateFailsWithoutScaffold() throws Exception {
        var result = new WebLogicCliService().validate(tempDir.resolve("upgrd-out"));
        assertFalse(result.success());
    }

    @Test
    void validatePassesWithCompleteScaffold() throws Exception {
        Path output = tempDir.resolve("upgrd-out");
        Path weblogic = output.resolve("migrated/deploy/weblogic");
        Files.createDirectories(weblogic);
        Files.writeString(weblogic.resolve("weblogic.xml"), "<weblogic-web-app><context-root>app-web</context-root></weblogic-web-app>");
        Files.writeString(weblogic.resolve("weblogic-application.xml"), "<weblogic-application/>");
        Files.writeString(weblogic.resolve("deploy.sh"), """
                #!/bin/sh
                WAR="../app-web/target/app-web-1.0.0-SNAPSHOT.war"
                mvn -Pproduction-weblogic package
                """);
        Files.writeString(weblogic.resolve("README.md"), "readme");

        Path warDir = output.resolve("migrated/app-web/target");
        Files.createDirectories(warDir);
        Files.writeString(warDir.resolve("app-web-1.0.0-SNAPSHOT.war"), "war");

        var result = new WebLogicCliService().validate(output);
        assertTrue(result.success(), result.message());
    }
}
