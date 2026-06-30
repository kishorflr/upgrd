package com.upgrd.core.model;

import java.util.List;

public record FeatureUsageEntry(
        String id,
        FeatureKind kind,
        String name,
        String detail,
        UsageObservation observation,
        FeatureHealth health,
        int hitCount,
        int errorHitCount,
        int successHitCount,
        List<String> observedInLogKinds,
        String sample,
        String errorSample,
        DeployPresence deployPresence,
        String migrationGuidance) {
}
