package com.upgrd.core.model;

public record WarMergeConflict(
        String qualifiedClassName,
        String sourceFile,
        String warClassEntry,
        WarConflictPolicy policy,
        String resolution) {
}
