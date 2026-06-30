package com.upgrd.core.model;

public record LogSourceEntry(
        String stagedFile,
        String originalName,
        String archiveSource,
        LogKind kind,
        int sequence) {
}
