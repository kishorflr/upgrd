package com.upgrd.core.model;

public record ApplyStepResult(
        String stepId,
        String recipe,
        String status,
        String message) {
}
