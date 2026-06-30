package com.upgrd.core.model;

import java.nio.file.Path;
import java.util.List;

public record AnalysisInput(
        Path sourceRoot,
        Path warFile,
        List<Path> logFiles,
        Path logsDir,
        Path outputDir,
        ProjectProfile profileOverride) {

    public AnalysisInput(Path sourceRoot, Path warFile, List<Path> logFiles, Path outputDir) {
        this(sourceRoot, warFile, logFiles, null, outputDir, null);
    }

    public AnalysisInput(
            Path sourceRoot,
            Path warFile,
            List<Path> logFiles,
            Path outputDir,
            ProjectProfile profileOverride) {
        this(sourceRoot, warFile, logFiles, null, outputDir, profileOverride);
    }
}
