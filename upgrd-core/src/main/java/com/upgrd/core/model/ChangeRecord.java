package com.upgrd.core.model;

import java.util.List;

public record ChangeRecord(
        String changeId,
        String ruleId,
        String category,
        String file,
        List<Integer> lineRange,
        String before,
        String after,
        String reason,
        List<String> evidence,
        String risk,
        boolean reversible,
        String recipeVersion,
        boolean automated) {
}
