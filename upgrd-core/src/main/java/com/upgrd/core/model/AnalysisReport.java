package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record AnalysisReport(
        String upgrdVersion,
        Instant generatedAt,
        ProjectDiscovery discovery,
        SyncReport sync,
        UsageReport usage) {
}
