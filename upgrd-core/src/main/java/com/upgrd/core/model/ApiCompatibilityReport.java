package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiCompatibilityReport(
        String upgrdVersion,
        Instant generatedAt,
        int totalHits,
        Map<ApiRemediationType, Integer> countsByRemediationType,
        List<ApiCompatibilityHit> hits) {

    public long countByType(ApiRemediationType type) {
        return hits.stream().filter(h -> h.remediationType() == type).count();
    }

    public String summary() {
        return String.format(
                "%d API compatibility hit(s): %d automated/replacement, %d manual, %d unsupported",
                totalHits,
                countsByRemediationType.getOrDefault(ApiRemediationType.AUTOMATED, 0)
                        + countsByRemediationType.getOrDefault(ApiRemediationType.REPLACEMENT, 0),
                countsByRemediationType.getOrDefault(ApiRemediationType.MANUAL, 0),
                countsByRemediationType.getOrDefault(ApiRemediationType.UNSUPPORTED, 0));
    }
}
