package com.upgrd.core.model;

import java.nio.file.Path;
import java.util.List;

public record AnalysisInput(
        Path sourceRoot,
        Path warFile,
        List<Path> logFiles,
        Path outputDir) {
}
