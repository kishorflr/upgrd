package com.upgrd.core.model;

public record BrokenAccessSignal(
        String featureKey,
        String pathOrClass,
        int errorHits,
        int successHits,
        String sample,
        String sourceLogKind) {
}
