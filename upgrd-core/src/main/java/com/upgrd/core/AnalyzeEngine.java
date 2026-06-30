package com.upgrd.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.design.DesignAdvisoryAnalyzer;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.logs.LogUsageAnalyzer;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.AnalysisReport;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.UsageReport;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.source.SourceInspector;
import com.upgrd.core.sync.SyncAnalyzer;
import com.upgrd.core.war.WarInspector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AnalyzeEngine {

    public static final String VERSION = "1.1.0-SNAPSHOT";

    private final ProjectDiscoveryService discoveryService = new ProjectDiscoveryService();
    private final DesignAdvisoryAnalyzer designAdvisoryAnalyzer = new DesignAdvisoryAnalyzer();
    private final WarInspector warInspector = new WarInspector();
    private final SourceInspector sourceInspector = new SourceInspector();
    private final SyncAnalyzer syncAnalyzer = new SyncAnalyzer();
    private final LogUsageAnalyzer logUsageAnalyzer = new LogUsageAnalyzer();
    private final ReportWriter reportWriter = new ReportWriter();
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public AnalysisReport analyze(AnalysisInput input) throws IOException {
        validate(input);

        ProjectDiscovery discovery = discoveryService.discover(input.sourceRoot(), input.profileOverride());
        Set<String> warClasses = warInspector.listApplicationClasses(input.warFile());
        Set<String> sourceClasses = sourceInspector.listSourceClasses(input.sourceRoot(), discovery.sourceRoots());
        SyncReport sync = syncAnalyzer.compare(warClasses, sourceClasses);
        UsageReport usage = logUsageAnalyzer.analyze(input.logFiles(), warClasses);
        var designAdvisory = designAdvisoryAnalyzer.analyze(
                input.sourceRoot(),
                discovery.sourceRoots(),
                discovery.profile(),
                discovery.fingerprint());

        return new AnalysisReport(VERSION, Instant.now(), discovery, sync, usage, designAdvisory);
    }

    public Path writeReport(AnalysisReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path reportFile = outputDir.resolve("analysis-report.json");
        mapper.writeValue(reportFile.toFile(), report);
        reportWriter.writeDesignAdvisory(report.designAdvisory(), outputDir);
        return reportFile;
    }

    private void validate(AnalysisInput input) throws IOException {
        if (!Files.isDirectory(input.sourceRoot())) {
            throw new IOException("Source root not found: " + input.sourceRoot());
        }
        if (!warInspector.isWar(input.warFile())) {
            throw new IOException("WAR file not found or invalid: " + input.warFile());
        }
        for (Path logFile : input.logFiles()) {
            if (!Files.isRegularFile(logFile)) {
                throw new IOException("Log file not found: " + logFile);
            }
        }
    }
}
