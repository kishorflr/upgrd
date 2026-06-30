package com.upgrd.core.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void bundlesAvailableReports() throws Exception {
        Files.writeString(tempDir.resolve("analysis-report.json"), "{\"version\":\"1.0\"}");
        Files.writeString(tempDir.resolve("change-ledger.json"), "{\"changes\":[]}");

        var result = new AuditExporter().export(tempDir);

        assertTrue(result.reportCount() >= 2);
        assertTrue(Files.isRegularFile(result.jsonFile()));
        assertTrue(Files.isRegularFile(result.markdownFile()));
        assertTrue(Files.readString(result.jsonFile()).contains("includedReports"));
    }
}
