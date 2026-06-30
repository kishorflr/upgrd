package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Result of {@code upgrd verify} against the migrated application.
 */
public record VerifyReport(
        String upgrdVersion,
        Instant verifiedAt,
        boolean passed,
        int exitCode,
        boolean securityScan,
        String command,
        String logFile,
        List<String> summaryLines) {
}
