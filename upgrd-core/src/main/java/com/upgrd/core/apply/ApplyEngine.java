package com.upgrd.core.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.ApplyStepResult;
import com.upgrd.core.model.ChangeLedger;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.report.ReportWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * M2 scaffold: creates migrated/ layout, change ledger preview, and per-step apply status.
 * OpenRewrite execution will be wired in a follow-up change.
 */
public final class ApplyEngine {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final ReportWriter reportWriter = new ReportWriter();

    public UpgradePlan loadPlan(Path planFile) throws IOException {
        return mapper.readValue(planFile.toFile(), UpgradePlan.class);
    }

    public ApplyReport apply(UpgradePlan plan, Path sourceRoot, Path outputDir) throws IOException {
        if (plan.dryRun()) {
            throw new IOException("Cannot apply a dry-run plan. Re-run `plan upgrade` with --dry-run=false.");
        }

        Path migratedRoot = outputDir.resolve("migrated");
        scaffoldOutputLayout(migratedRoot);

        List<ApplyStepResult> results = new ArrayList<>();
        for (UpgradeStep step : plan.steps()) {
            if (step.mode() == StepMode.ADVISORY) {
                results.add(new ApplyStepResult(
                        step.id(),
                        step.recipe(),
                        "ADVISORY",
                        "Advisory step — review in design-advisory.json and audit UI; not auto-applied"));
                continue;
            }
            results.add(new ApplyStepResult(
                    step.id(),
                    step.recipe(),
                    "PENDING",
                    "Recipe execution not yet implemented (M2 in progress)"));
        }

        ChangeLedger ledger = reportWriter.previewFromPlan(plan, sourceRoot);
        reportWriter.writeChangeLedger(ledger, outputDir);

        ApplyReport report = new ApplyReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                sourceRoot.toAbsolutePath().normalize().toString(),
                migratedRoot.toAbsolutePath().normalize().toString(),
                results);

        writeReport(report, outputDir);
        return report;
    }

    public Path writeReport(ApplyReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path reportFile = outputDir.resolve("apply-report.json");
        mapper.writeValue(reportFile.toFile(), report);
        return reportFile;
    }

    private void scaffoldOutputLayout(Path migratedRoot) throws IOException {
        Files.createDirectories(migratedRoot.resolve("app-web"));
        Files.createDirectories(migratedRoot.resolve("deploy/wildfly"));
        Files.createDirectories(migratedRoot.resolve("deploy/weblogic"));
        Path pom = migratedRoot.resolve("pom.xml");
        if (!Files.exists(pom)) {
            Files.writeString(pom, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <!-- UpGrd M2 scaffold: replace with generated Maven parent POM -->
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                      <modelVersion>4.0.0</modelVersion>
                      <groupId>com.example.migrated</groupId>
                      <artifactId>migrated-parent</artifactId>
                      <version>1.0.0-SNAPSHOT</version>
                      <packaging>pom</packaging>
                    </project>
                    """);
        }
    }
}
