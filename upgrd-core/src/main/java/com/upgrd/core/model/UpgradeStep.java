package com.upgrd.core.model;

import java.util.List;

public record UpgradeStep(
        String id,
        String category,
        String description,
        String recipe,
        String reason,
        List<String> evidence,
        StepMode mode,
        ChangeClassification classification) {
}
