package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sanitized failure context safe to share with external AI platforms.
 * Business identifiers, paths, and secrets are redacted or tokenized.
 */
public record AnonymousFailureReport(
        String reportVersion,
        Instant generatedAt,
        String failureKind,
        String summary,
        List<SanitizedTestFailure> testFailures,
        List<String> sanitizedStackFrames,
        List<String> sanitizedLogExcerpt,
        Map<String, String> environment,
        String aiPromptMarkdown,
        List<String> redactionNotes) {

    public record SanitizedTestFailure(
            String testClass,
            String testMethod,
            String message,
            List<String> stackFrames) {
    }
}
