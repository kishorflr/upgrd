package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record WarMergeReport(
        String upgrdVersion,
        Instant mergedAt,
        String warPath,
        WarConflictPolicy policy,
        int mergedLibCount,
        int extractedClassCount,
        int stubCount,
        int conflictCount,
        List<String> mergedLibs,
        List<String> extractedClasses,
        List<String> generatedStubs,
        List<WarMergeConflict> conflicts) {
}
