package com.upgrd.core.model;

public record StepApproval(
        String stepId,
        boolean approved,
        ChangeClassification classification,
        StepMode mode,
        String note) {
}
