package com.upgrd.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.antipattern.AntiPatternAnalyzer;
import com.upgrd.core.compat.ApiCompatibilityAnalyzer;
import com.upgrd.core.design.DesignAdvisoryAnalyzer;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.documentation.ApplicationDocumenter;
import com.upgrd.core.documentation.DocumentationWriter;
import com.upgrd.core.logs.LogUsageAnalyzer;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.AnalysisReport;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.UsageReport;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityAnalyzer;
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

    public static final String VERSION = "1.7.0";

    private final ProjectDiscoveryService discoveryService = new ProjectDiscoveryService();
    private final DesignAdvisoryAnalyzer designAdvisoryAnalyzer = new DesignAdvisoryAnalyzer();
    private final AntiPatternAnalyzer antiPatternAnalyzer = new AntiPatternAnalyzer();
    private final ApiCompatibilityAnalyzer apiCompatibilityAnalyzer = new ApiCompatibilityAnalyzer();
    private final SecurityAnalyzer securityAnalyzer = new SecurityAnalyzer();
    private final ApplicationDocumenter applicationDocumenter = new ApplicationDocumenter();
    private final DocumentationWriter documentationWriter = new DocumentationWriter();
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
        Set<String> warClasses = input.warFile() != null
                ? warInspector.listApplicationClasses(input.warFile())
                : Set.of();
        Set<String> warLibs = input.warFile() != null
                ? warInspector.listLibraryJars(input.warFile())
                : Set.of();
        Set<String> sourceClasses = sourceInspector.listSourceClasses(input.sourceRoot(), discovery.sourceRoots());
        Set<String> sourceLibs = sourceInspector.listLibraryJars(input.sourceRoot());
        SyncReport sync = syncAnalyzer.compare(warClasses, sourceClasses, warLibs, sourceLibs);
        UsageReport usage = logUsageAnalyzer.analyze(input.logFiles(), warClasses);
        var designAdvisory = designAdvisoryAnalyzer.analyze(
                input.sourceRoot(),
                discovery.sourceRoots(),
                discovery.profile(),
                discovery.fingerprint());
        var antiPatterns = antiPatternAnalyzer.analyze(
                input.sourceRoot(), discovery.sourceRoots(), discovery.profile());
        var apiCompatibility = apiCompatibilityAnalyzer.analyze(input.sourceRoot(), discovery);
        var security = securityAnalyzer.analyze(input.sourceRoot(), discovery);

        AnalysisReport report = new AnalysisReport(
                VERSION, Instant.now(), discovery, sync, usage, designAdvisory);

        writeReport(report, security, antiPatterns, apiCompatibility, input, input.sourceRoot(), input.outputDir());
        return report;
    }

    public Path writeReport(AnalysisReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path reportFile = outputDir.resolve("analysis-report.json");
        mapper.writeValue(reportFile.toFile(), report);
        reportWriter.writeDesignAdvisory(report.designAdvisory(), outputDir);
        return reportFile;
    }

    private void writeReport(
            AnalysisReport report,
            com.upgrd.core.model.SecurityReport security,
            com.upgrd.core.model.AntiPatternReport antiPatterns,
            com.upgrd.core.model.ApiCompatibilityReport apiCompatibility,
            AnalysisInput input,
            Path sourceRoot,
            Path outputDir) throws IOException {
        writeReport(report, outputDir);
        reportWriter.writeSecurityReport(security, outputDir);
        reportWriter.writeAntiPatternReport(antiPatterns, outputDir);
        reportWriter.writeApiCompatibilityReport(apiCompatibility, outputDir);
        mapper.writeValue(outputDir.resolve("usage-report.json").toFile(), report.usage());
        mapper.writeValue(outputDir.resolve("sync-report.json").toFile(), report.sync());
        if (input.warFile() != null) {
            mapper.writeValue(outputDir.resolve("war-context.json").toFile(), new com.upgrd.core.model.WarContext(
                    input.warFile().toAbsolutePath().normalize().toString(),
                    com.upgrd.core.model.WarConflictPolicy.WAR_WINS));
        }
        var documentation = applicationDocumenter.documentAnalyzePhase(
                report, security, sourceRoot.toAbsolutePath().normalize().toString());
        documentationWriter.write(documentation, outputDir);
    }

    private void validate(AnalysisInput input) throws IOException {
        if (!Files.isDirectory(input.sourceRoot())) {
            throw new IOException("Source root not found: " + input.sourceRoot());
        }
        if (input.warFile() != null && !warInspector.isWar(input.warFile())) {
            throw new IOException("WAR file not found or invalid: " + input.warFile());
        }
        for (Path logFile : input.logFiles()) {
            if (!Files.isRegularFile(logFile)) {
                throw new IOException("Log file not found: " + logFile);
            }
        }
    }
}
