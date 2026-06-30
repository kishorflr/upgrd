package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FeatureUsageReport(
        String upgrdVersion,
        Instant generatedAt,
        int logFileCount,
        int totalFeatures,
        int observedCount,
        int unobservedCount,
        int healthyCount,
        int brokenCount,
        Map<FeatureKind, Integer> observedByKind,
        Map<FeatureKind, Integer> unobservedByKind,
        Map<String, Integer> hitsByLogKind,
        List<LogSourceEntry> logSources,
        List<FeatureUsageEntry> features,
        List<String> notes) {
}
