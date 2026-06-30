package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record SecurityReport(
        String upgrdVersion,
        Instant generatedAt,
        ProjectProfile profile,
        List<SecurityFinding> findings,
        int remediatedCount,
        int openCount) {
}
