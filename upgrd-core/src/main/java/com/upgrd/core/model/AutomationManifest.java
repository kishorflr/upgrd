package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable metadata embedded in the migrated application for automation and AI tools.
 */
public record AutomationManifest(
        String upgrdVersion,
        Instant generatedAt,
        ProjectProfile profile,
        String testCommand,
        Map<String, String> layout,
        List<String> entryPoints,
        List<String> analysisHints) {
}
