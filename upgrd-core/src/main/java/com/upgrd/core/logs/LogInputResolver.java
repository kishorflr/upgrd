package com.upgrd.core.logs;

import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.LogSourceManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LogInputResolver {

    private final LogArchiveResolver archiveResolver = new LogArchiveResolver();

    public ResolvedLogInput resolve(AnalysisInput input) throws IOException {
        List<Path> explicit = input.logFiles() != null ? input.logFiles() : List.of();
        LogSourceManifest manifest = null;
        List<Path> resolved = new ArrayList<>(explicit);

        if (input.logsDir() != null) {
            Path stagingDir = input.outputDir().resolve(".upgrd/log-staging");
            manifest = archiveResolver.resolve(input.logsDir(), stagingDir);
            resolved.addAll(archiveResolver.stagedPaths(manifest));
        }

        Set<Path> unique = new LinkedHashSet<>();
        for (Path path : resolved) {
            if (path != null && Files.isRegularFile(path)) {
                unique.add(path.toAbsolutePath().normalize());
            }
        }

        return new ResolvedLogInput(List.copyOf(unique), manifest);
    }

    public record ResolvedLogInput(List<Path> logFiles, LogSourceManifest manifest) {
    }
}
