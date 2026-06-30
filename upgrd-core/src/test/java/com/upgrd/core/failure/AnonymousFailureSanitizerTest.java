package com.upgrd.core.failure;

import com.upgrd.core.model.AnonymousFailureReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousFailureSanitizerTest {

    private final AnonymousFailureSanitizer sanitizer = new AnonymousFailureSanitizer();

    @Test
    void redactsPathsSecretsAndApplicationTypes() {
        String raw = """
                [ERROR] com.acme.billing.InvoiceServiceTest.processInvoice -- Time elapsed: 0.1 s <<< FAILURE!
                org.opentest4j.AssertionFailedError: expected true but was false
                    at com.acme.billing.InvoiceService.process(/Users/acme/projects/billing/src/InvoiceService.java:42)
                    at org.junit.jupiter.api.Assertions.assertTrue(Assertions.java:63)
                password=supersecret jdbc:mysql://db.internal/acme?user=admin&password=hunter2
                """;

        AnonymousFailureReport report = sanitizer.sanitize(
                raw,
                "VERIFY_FAILURE",
                List.of("com.acme.billing"),
                Map.of("java.version", "21.0.1"));

        assertTrue(report.summary().contains("VERIFY_FAILURE"));
        assertFalse(report.aiPromptMarkdown().contains("com.acme.billing"));
        assertFalse(report.aiPromptMarkdown().contains("/Users/acme"));
        assertFalse(report.aiPromptMarkdown().contains("supersecret"));
        assertFalse(report.aiPromptMarkdown().contains("hunter2"));
        assertTrue(report.aiPromptMarkdown().contains("app.Type_"));
        assertTrue(report.aiPromptMarkdown().contains("org.junit"));
        assertTrue(report.redactionNotes().stream().anyMatch(n -> n.contains("path")));
    }

    @Test
    void producesPasteReadyMarkdownPrompt() {
        AnonymousFailureReport report = sanitizer.sanitize(
                "[ERROR] com.example.UserActionSmokeTest.classLoads -- Time elapsed: 0.01 s <<< FAILURE!\n"
                        + "java.lang.ClassNotFoundException: com.example.UserAction\n",
                "VERIFY_FAILURE",
                List.of("com.example"),
                Map.of());

        assertTrue(report.aiPromptMarkdown().startsWith("# Anonymous failure report"));
        assertTrue(report.aiPromptMarkdown().contains("## Ask"));
        assertFalse(report.aiPromptMarkdown().contains("com.example.UserAction"));
    }
}
