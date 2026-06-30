package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AntiPatternReport(
        String upgrdVersion,
        Instant analyzedAt,
        ProjectProfile profile,
        int totalFindings,
        Map<String, Integer> countsBySeverity,
        List<AntiPatternFinding> findings) {
}
