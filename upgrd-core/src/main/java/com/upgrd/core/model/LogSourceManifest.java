package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

public record LogSourceManifest(
        String stagingDir,
        Instant generatedAt,
        int archiveCount,
        int extractedFileCount,
        int plainFileCount,
        List<LogSourceEntry> entries) {
}
