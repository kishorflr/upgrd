package com.upgrd.core.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatedReportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void pagesApiCompatibilityHits() throws Exception {
        Files.writeString(tempDir.resolve("api-compatibility-report.json"), """
                {
                  "totalHits": 3,
                  "summary": "3 hits",
                  "hits": [
                    {"file": "A.java", "api": "javax.servlet", "remediationType": "AUTOMATED"},
                    {"file": "B.java", "api": "org.apache.struts", "remediationType": "MANUAL"},
                    {"file": "C.java", "api": "log4j", "remediationType": "AUTOMATED"}
                  ]
                }
                """);

        PaginatedReportService service = new PaginatedReportService();
        var page = service.page(tempDir, "api-compatibility-report.json", 1, 1, null);

        assertEquals(3, page.total());
        assertEquals(1, page.offset());
        assertEquals(1, page.items().size());
        assertEquals("B.java", page.items().get(0).get("file").asText());
        assertFalse(page.summary().has("hits"));
    }

    @Test
    void filtersPreviewLedgerByClassification() throws Exception {
        Files.writeString(tempDir.resolve("change-ledger-preview.json"), """
                {
                  "changes": [
                    {"file": "a", "classification": "MANDATORY"},
                    {"file": "b", "classification": "RECOMMENDED"},
                    {"file": "c", "classification": "MANDATORY"}
                  ]
                }
                """);

        var page = new PaginatedReportService()
                .page(tempDir, "change-ledger-preview.json", 0, 10, "MANDATORY");

        assertEquals(2, page.total());
        assertEquals("a", page.items().get(0).get("file").asText());
    }

    @Test
    void filtersFeatureUsageByHealth() throws Exception {
        Files.writeString(tempDir.resolve("feature-usage-report.json"), """
                {
                  "totalFeatures": 3,
                  "healthyCount": 1,
                  "brokenCount": 1,
                  "unobservedCount": 1,
                  "features": [
                    {"name": "Login", "health": "HEALTHY"},
                    {"name": "Admin", "health": "UNOBSERVED"},
                    {"name": "Broken", "health": "BROKEN"}
                  ]
                }
                """);

        var page = new PaginatedReportService()
                .page(tempDir, "feature-usage-report.json", 0, 50, "BROKEN");

        assertEquals(1, page.total());
        assertEquals("Broken", page.items().get(0).get("name").asText());
    }
}
