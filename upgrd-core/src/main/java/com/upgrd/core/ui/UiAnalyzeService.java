package com.upgrd.core.ui;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.AnalysisReport;
import com.upgrd.core.model.AnalyzeWorkspace;
import com.upgrd.core.model.FeatureUsageReport;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.report.ReportWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class UiAnalyzeService {

    private final AnalyzeEngine analyzeEngine = new AnalyzeEngine();
    private final ReportWriter reportWriter = new ReportWriter();
    private final WorkspaceStore workspaceStore = new WorkspaceStore();

    public void saveWorkspace(Path outputDir, AnalyzeWorkspace workspace) throws IOException {
        workspaceStore.save(outputDir, workspace);
    }

    public AnalyzeWorkspace loadWorkspace(Path outputDir) throws IOException {
        return workspaceStore.load(outputDir);
    }

    public AnalyzeResult analyzeLogs(Path outputDir, String logsDirOverride) throws IOException {
        AnalyzeWorkspace workspace = workspaceStore.load(outputDir);
        if (workspace == null || workspace.sourceRoot() == null || workspace.warPath() == null) {
            throw new IOException("Workspace not configured — set source and WAR paths in the Coverage tab or "
                    + "start the UI with: upgrd run --serve-ui --source ./app --war ./app.war --output ./upgrd-out");
        }

        Path source = Path.of(workspace.sourceRoot());
        Path war = Path.of(workspace.warPath());
        String logsDirValue = logsDirOverride != null && !logsDirOverride.isBlank()
                ? logsDirOverride
                : workspace.logsDir();
        if (logsDirValue == null || logsDirValue.isBlank()) {
            throw new IOException("Logs directory is required");
        }
        Path logsPath = Path.of(logsDirValue);

        if (!Files.isDirectory(source)) {
            throw new IOException("Source root not found: " + source);
        }
        if (!Files.isRegularFile(war)) {
            throw new IOException("WAR file not found: " + war);
        }
        if (!Files.isDirectory(logsPath)) {
            throw new IOException("Logs directory not found: " + logsPath);
        }

        AnalyzeWorkspace updated = new AnalyzeWorkspace(
                source.toAbsolutePath().normalize().toString(),
                war.toAbsolutePath().normalize().toString(),
                outputDir.toAbsolutePath().normalize().toString(),
                logsPath.toAbsolutePath().normalize().toString());
        workspaceStore.save(outputDir, updated);

        AnalysisReport report = analyzeEngine.analyze(new AnalysisInput(
                source,
                war,
                List.of(),
                logsPath,
                outputDir,
                null));

        FeatureUsageReport featureUsage = reportWriter.readFeatureUsageReport(outputDir);
        return new AnalyzeResult(
                true,
                report.sync().warClassCount(),
                report.sync().sourceClassCount(),
                report.usage().totalHits(),
                featureUsage != null ? featureUsage.observedCount() : 0,
                featureUsage != null ? featureUsage.unobservedCount() : 0,
                featureUsage != null ? featureUsage.healthyCount() : 0,
                featureUsage != null ? featureUsage.brokenCount() : 0,
                featureUsage != null ? featureUsage.logSources().size() : report.usage().logSources().size(),
                Map.of(
                        "analysis-report.json", true,
                        "feature-usage-report.json", featureUsage != null,
                        "log-source-manifest.json", Files.isRegularFile(outputDir.resolve("log-source-manifest.json"))));
    }

    public record AnalyzeResult(
            boolean success,
            int warClassCount,
            int sourceClassCount,
            int logHits,
            int observedFeatures,
            int unobservedFeatures,
            int healthyFeatures,
            int brokenFeatures,
            int stagedLogFiles,
            Map<String, Boolean> reports) {
    }
}
