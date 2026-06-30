package com.upgrd.core.model;

public record AnalyzeWorkspace(
        String sourceRoot,
        String warPath,
        String outputDir,
        String logsDir) {
}
