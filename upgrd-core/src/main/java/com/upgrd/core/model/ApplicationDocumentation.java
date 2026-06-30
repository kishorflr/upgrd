package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Structured knowledge base for future human and agent analysis of the application.
 */
public record ApplicationDocumentation(
        String upgrdVersion,
        Instant generatedAt,
        String sourceRoot,
        ProjectProfile profile,
        String summary,
        List<DocumentationSection> sections,
        List<String> reportIndex) {
}
