package com.upgrd.core.model;

public record UpgradeStep(
        String id,
        String category,
        String description,
        String recipe) {
}
