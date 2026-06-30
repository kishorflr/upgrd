package com.upgrd.core.model;

import java.util.List;

public record ApiCompatibilityHit(
        String hitId,
        String catalogId,
        String api,
        ApiRemediationType remediationType,
        String file,
        List<Integer> lineRange,
        String snippet,
        String replacement,
        String description,
        String recipeId,
        String planStepId) {
}
