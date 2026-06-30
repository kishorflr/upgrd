package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record ChangeLedger(
        String upgrdVersion,
        Instant generatedAt,
        String sourceRoot,
        ProjectProfile profile,
        List<ChangeRecord> changes) {
}
