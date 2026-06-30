package com.upgrd.core.failure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureReportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void writesJsonAndMarkdownReports() throws Exception {
        Path migrated = tempDir.resolve("migrated");
        Path mainJava = migrated.resolve("app-web/src/main/java/com/example");
        Files.createDirectories(mainJava);
        Files.writeString(mainJava.resolve("UserAction.java"), "package com.example;\nclass UserAction {}\n");

        Path reportDir = migrated.resolve(".upgrd/failure-report");
        String log = """
                [ERROR] com.example.UserActionSmokeTest.classLoads -- Time elapsed: 0.01 s <<< FAILURE!
                java.lang.ClassNotFoundException: com.example.UserAction
                """;

        List<String> written = new FailureReportService().generateFromText(
                log, reportDir, "VERIFY_FAILURE", migrated);

        assertTrue(written.stream().anyMatch(p -> p.endsWith("anonymous-failure-report.json")));
        assertTrue(written.stream().anyMatch(p -> p.endsWith("anonymous-failure-report.md")));
        assertTrue(Files.readString(reportDir.resolve("anonymous-failure-report.md"))
                .contains("Anonymous failure report"));
    }
}
