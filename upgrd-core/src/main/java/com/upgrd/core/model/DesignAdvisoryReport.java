package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record DesignAdvisoryReport(
        String upgrdVersion,
        Instant generatedAt,
        ProjectProfile profile,
        List<DesignAdvisory> advisories) {
}
