package com.upgrd.core.verify;

import com.upgrd.core.model.VerifyReport.WildflySmoke;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyReportWriterTest {

    @Test
    void summarizeIncludesWildflyNotes() {
        var writer = new VerifyReportWriter();
        var report = writer.build(
                true,
                0,
                false,
                "mvn verify",
                null,
                "BUILD SUCCESS",
                new WildflySmoke(true, true, true, false, false, true, List.of("Container running")));

        assertTrue(report.summaryLines().stream().anyMatch(l -> l.contains("WildFly smoke")));
        assertTrue(report.summaryLines().stream().anyMatch(l -> l.contains("staged/deployed")));
    }
}
