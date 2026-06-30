package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * User-approved subset of an upgrade plan — required before apply (M16).
 */
public record ApprovedPlan(
        String upgrdVersion,
        Instant generatedAt,
        String planUpgrdVersion,
        String sourceRoot,
        List<StepApproval> steps) {

    public boolean isApproved(String stepId) {
        return steps.stream()
                .filter(s -> s.stepId().equals(stepId))
                .findFirst()
                .map(StepApproval::approved)
                .orElse(false);
    }

    public long approvedCount() {
        return steps.stream().filter(StepApproval::approved).count();
    }

    public Optional<StepApproval> find(String stepId) {
        return steps.stream().filter(s -> s.stepId().equals(stepId)).findFirst();
    }
}
