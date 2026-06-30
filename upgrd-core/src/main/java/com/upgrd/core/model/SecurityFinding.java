package com.upgrd.core.model;

import java.util.List;

public record SecurityFinding(
        String findingId,
        String severity,
        String category,
        String cveId,
        String file,
        List<Integer> lineRange,
        String description,
        String remediation,
        String recipeId,
        boolean autoFixable,
        boolean remediated) {
}
