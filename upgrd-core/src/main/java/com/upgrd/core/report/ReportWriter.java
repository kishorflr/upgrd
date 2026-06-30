package com.upgrd.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AntiPatternReport;
import com.upgrd.core.model.ApplicationDocumentation;
import com.upgrd.core.model.ChangeLedger;
import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ChangeRecord;
import com.upgrd.core.model.DesignAdvisoryReport;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.SecurityReport;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradePreviewReport;
import com.upgrd.core.model.UpgradeStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes accountability reports (change ledger, design advisory, security, documentation) to the output directory.
 */
public final class ReportWriter {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Path writeDesignAdvisory(DesignAdvisoryReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("design-advisory.json");
        mapper.writeValue(file.toFile(), report);
        return file;
    }

    public Path writeAntiPatternReport(AntiPatternReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("anti-pattern-report.json");
        mapper.writeValue(file.toFile(), report);
        return file;
    }

    public Path writeSecurityReport(SecurityReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("security-report.json");
        mapper.writeValue(file.toFile(), report);
        return file;
    }

    public ApplicationDocumentation readDocumentation(Path outputDir) throws IOException {
        Path file = outputDir.resolve("app-documentation.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return mapper.readValue(file.toFile(), ApplicationDocumentation.class);
    }

    public SecurityReport readSecurityReport(Path outputDir) throws IOException {
        Path file = outputDir.resolve("security-report.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return mapper.readValue(file.toFile(), SecurityReport.class);
    }

    public com.upgrd.core.model.UsageReport readUsageReport(Path outputDir) throws IOException {
        Path usageFile = outputDir.resolve("usage-report.json");
        if (Files.isRegularFile(usageFile)) {
            return mapper.readValue(usageFile.toFile(), com.upgrd.core.model.UsageReport.class);
        }
        Path analysisFile = outputDir.resolve("analysis-report.json");
        if (!Files.isRegularFile(analysisFile)) {
            return null;
        }
        var analysis = mapper.readTree(Files.readString(analysisFile));
        if (analysis.has("usage")) {
            return mapper.treeToValue(analysis.get("usage"), com.upgrd.core.model.UsageReport.class);
        }
        return null;
    }

    public Path writeChangeLedger(ChangeLedger ledger, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("change-ledger.json");
        mapper.writeValue(file.toFile(), ledger);
        return file;
    }

    /**
     * Creates a plan-time change ledger preview from upgrade steps (before/after filled on apply).
     */
    public ChangeLedger previewFromPlan(UpgradePlan plan, Path sourceRoot) {
        AtomicInteger counter = new AtomicInteger();
        List<ChangeRecord> changes = new ArrayList<>();

        for (UpgradeStep step : plan.steps()) {
            if (step.mode() == com.upgrd.core.model.StepMode.ADVISORY) {
                continue;
            }
            changes.add(new ChangeRecord(
                    "plan-" + step.id() + "-" + String.format("%04d", counter.incrementAndGet()),
                    step.recipe(),
                    step.category(),
                    "(pending apply)",
                    List.of(),
                    "",
                    "",
                    step.reason(),
                    step.evidence(),
                    "PENDING",
                    true,
                    AnalyzeEngine.VERSION,
                    step.mode() == com.upgrd.core.model.StepMode.AUTOMATED,
                    step.classification()));
        }

        return new ChangeLedger(
                AnalyzeEngine.VERSION,
                Instant.now(),
                sourceRoot.toAbsolutePath().normalize().toString(),
                plan.profile() != null ? plan.profile() : ProjectProfile.UNKNOWN,
                changes);
    }

    public Path writeUpgradePreviewReport(UpgradePreviewReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("upgrade-preview-report.json");
        mapper.writeValue(file.toFile(), report);
        ChangeLedger previewLedger = new ChangeLedger(
                report.version(),
                report.generatedAt(),
                report.sourceRoot(),
                report.profile() != null ? report.profile() : ProjectProfile.UNKNOWN,
                report.changes());
        mapper.writeValue(outputDir.resolve("change-ledger-preview.json").toFile(), previewLedger);
        return file;
    }
}
