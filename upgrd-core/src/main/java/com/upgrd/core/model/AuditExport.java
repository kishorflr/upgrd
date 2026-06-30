package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Bundled audit export for compliance sign-off and offline review.
 */
public record AuditExport(
        String upgrdVersion,
        Instant exportedAt,
        String outputDirectory,
        List<String> includedReports,
        Map<String, Object> reports) {
}
