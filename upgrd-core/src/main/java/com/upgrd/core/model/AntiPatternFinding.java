package com.upgrd.core.model;

import java.util.List;

public record AntiPatternFinding(
        String id,
        String ruleId,
        String category,
        String file,
        List<Integer> lineRange,
        String pattern,
        String severity,
        String suggestion,
        String rationale,
        List<String> evidence) {
}
