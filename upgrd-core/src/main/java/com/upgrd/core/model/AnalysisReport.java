package com.upgrd.core.model;

import java.time.Instant;

public record AnalysisReport(
        String upgrdVersion,
        Instant generatedAt,
        ProjectDiscovery discovery,
        SyncReport sync,
        UsageReport usage,
        DesignAdvisoryReport designAdvisory) {
}
