package com.upgrd.core.export;

import com.upgrd.core.model.AuditExport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPdfExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesPdfSummary() throws Exception {
        AuditExport bundle = new AuditExport(
                "1.6.0-SNAPSHOT",
                Instant.now(),
                tempDir.toString(),
                List.of("analysis-report.json"),
                Map.of("analysis_report", Map.of("version", "1.0")));

        Path pdf = new AuditPdfExporter().export(bundle, tempDir);
        assertTrue(java.nio.file.Files.size(pdf) > 100);
    }
}
