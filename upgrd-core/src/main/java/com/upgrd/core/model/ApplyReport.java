package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record ApplyReport(
        String upgrdVersion,
        Instant appliedAt,
        String sourceRoot,
        String migratedRoot,
        List<ApplyStepResult> steps) {
}
