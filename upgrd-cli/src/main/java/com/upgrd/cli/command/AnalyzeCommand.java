package com.upgrd.cli.command;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.AnalysisReport;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.report.ReportWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "analyze",
        description = "Analyze source, WAR, and logs; write analysis-report.json")
public final class AnalyzeCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "Java project source root")
    private Path source;

    @Option(names = "--war", required = true, description = "Deployed WAR file")
    private Path war;

    @Option(names = "--logs", split = ",", description = "Comma-separated log file paths")
    private List<Path> logs = new ArrayList<>();

    @Option(names = "--logs-dir", description = "Directory of plain/.gz/.zip log archives (collision-safe staging)")
    private Path logsDir;

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Option(names = "--profile", description = "Override profile: legacy-web, legacy-backend")
    private String profile;

    @Override
    public Integer call() throws Exception {
        ProjectProfile profileOverride = parseProfile(profile);
        AnalyzeEngine engine = new AnalyzeEngine();
        AnalysisReport report = engine.analyze(new AnalysisInput(
                source, war, logs, logsDir, output, profileOverride));
        Path reportFile = engine.writeReport(report, output);

        var fp = report.discovery().fingerprint();
        System.out.printf("UpGrd analysis complete.%n");
        System.out.printf("  Profile: %s | Build: %s | Java: %s%n",
                report.discovery().profile(), report.discovery().buildSystem(),
                report.discovery().javaVersionHint());
        System.out.printf("  Frameworks: %s | Logging: %s%n",
                String.join(", ", fp.frameworks().isEmpty() ? List.of("none") : fp.frameworks()),
                fp.logging());
        System.out.printf("  Risk signals: %d | Design advisories: %d%n",
                fp.riskSignals().size(), report.designAdvisory().advisories().size());
        System.out.printf("  Documentation: %s/app-documentation.json%n", output.toAbsolutePath());
        System.out.printf("  Agent guide: %s/AGENTS.md%n", output.toAbsolutePath());
        System.out.printf("  WAR classes: %d | Source classes: %d%n",
                report.sync().warClassCount(), report.sync().sourceClassCount());
        System.out.printf("  Log hits: %d | Unused WAR classes: %d%n",
                report.usage().totalHits(), report.usage().unusedInWar().size());
        var featureUsage = new ReportWriter().readFeatureUsageReport(output);
        if (featureUsage != null) {
            System.out.printf("  Feature coverage: %d observed / %d unobserved / %d broken%n",
                    featureUsage.observedCount(), featureUsage.unobservedCount(), featureUsage.brokenCount());
        }
        System.out.printf("  Report: %s%n", reportFile.toAbsolutePath());
        System.out.printf("  Design advisory: %s/design-advisory.json%n", output.toAbsolutePath());
        System.out.printf("  Feature usage: %s/feature-usage-report.json%n", output.toAbsolutePath());
        return 0;
    }

    private ProjectProfile parseProfile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.toLowerCase().replace('_', '-')) {
            case "legacy-web" -> ProjectProfile.LEGACY_WEB;
            case "legacy-backend" -> ProjectProfile.LEGACY_BACKEND;
            default -> throw new IllegalArgumentException("Unknown profile: " + value
                    + " (use legacy-web or legacy-backend)");
        };
    }
}
