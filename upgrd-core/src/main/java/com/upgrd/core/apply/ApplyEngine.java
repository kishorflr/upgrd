package com.upgrd.core.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.ApplyStepResult;
import com.upgrd.core.model.ChangeLedger;
import com.upgrd.core.model.ChangeRecord;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.recipes.FileRecipe;
import com.upgrd.recipes.RecipeCatalog;
import com.upgrd.recipes.RecipeDefinition;
import com.upgrd.recipes.RecipeExecutor;
import com.upgrd.recipes.RecipeRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ApplyEngine {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final ReportWriter reportWriter = new ReportWriter();
    private final SourceMigrator sourceMigrator = new SourceMigrator();
    private final MavenScaffolder mavenScaffolder = new MavenScaffolder();
    private final RecipeRegistry recipeRegistry = new RecipeRegistry();
    private final RecipeExecutor recipeExecutor = new RecipeExecutor();
    private final RecipeCatalog recipeCatalog = new RecipeCatalog();

    public UpgradePlan loadPlan(Path planFile) throws IOException {
        return mapper.readValue(planFile.toFile(), UpgradePlan.class);
    }

    public ApplyReport apply(UpgradePlan plan, Path sourceRoot, Path outputDir) throws IOException {
        if (plan.dryRun()) {
            throw new IOException("Cannot apply a dry-run plan. Re-run `plan upgrade` with --dry-run=false.");
        }

        Path migratedRoot = outputDir.resolve("migrated");
        Path appWebRoot = migratedRoot.resolve("app-web");
        scaffoldOutputLayout(migratedRoot);

        List<ChangeRecord> allChanges = new ArrayList<>();
        List<ApplyStepResult> results = new ArrayList<>();
        AtomicInteger changeCounter = new AtomicInteger();

        for (UpgradeStep step : plan.steps()) {
            if (step.mode() == StepMode.ADVISORY) {
                results.add(new ApplyStepResult(
                        step.id(),
                        step.recipe(),
                        "ADVISORY",
                        "Advisory step — review in design-advisory.json and audit UI; not auto-applied"));
                continue;
            }

            ApplyStepResult stepResult = executeStep(
                    step, plan, sourceRoot, migratedRoot, appWebRoot, allChanges, changeCounter);
            results.add(stepResult);
        }

        ChangeLedger ledger = new ChangeLedger(
                AnalyzeEngine.VERSION,
                Instant.now(),
                sourceRoot.toAbsolutePath().normalize().toString(),
                plan.profile(),
                allChanges);
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

    private ApplyStepResult executeStep(
            UpgradeStep step,
            UpgradePlan plan,
            Path sourceRoot,
            Path migratedRoot,
            Path appWebRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        return switch (step.id()) {
            case "convert-maven" -> runConvertMaven(step, plan, sourceRoot, migratedRoot, appWebRoot,
                    allChanges, changeCounter);
            default -> runRecipe(step, appWebRoot.resolve("src/main/java"), allChanges, changeCounter);
        };
    }

    private ApplyStepResult runConvertMaven(
            UpgradeStep step,
            UpgradePlan plan,
            Path sourceRoot,
            Path migratedRoot,
            Path appWebRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        List<String> copied = sourceMigrator.copyToMavenLayout(sourceRoot, appWebRoot);
        mavenScaffolder.scaffold(migratedRoot, plan.profile(), plan.targetJava());

        allChanges.add(new ChangeRecord(
                "convert-maven-" + String.format("%04d", changeCounter.incrementAndGet()),
                step.recipe(),
                step.category(),
                "migrated/",
                List.of(),
                "(legacy layout)",
                "Maven multi-module layout with app-web/",
                step.reason(),
                List.copyOf(copied.stream().limit(10).toList()),
                "LOW",
                true,
                AnalyzeEngine.VERSION,
                true));

        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Copied " + copied.size() + " file(s) and generated Maven POMs");
    }

    private ApplyStepResult runRecipe(
            UpgradeStep step,
            Path javaRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        RecipeDefinition definition = recipeCatalog.findByStepId(step.id()).orElse(null);
        if (definition == null || !definition.implemented()) {
            return new ApplyStepResult(step.id(), step.recipe(), "PENDING",
                    "Recipe execution not yet implemented");
        }

        var recipe = recipeRegistry.resolve(step.recipe());
        if (recipe.isEmpty()) {
            return new ApplyStepResult(step.id(), step.recipe(), "SKIPPED",
                    "No executable recipe registered for " + step.recipe());
        }

        if (!Files.isDirectory(javaRoot)) {
            return new ApplyStepResult(step.id(), step.recipe(), "SKIPPED",
                    "No Java sources yet — run convert-maven first or ensure src/main/java exists");
        }

        RecipeExecutor.RecipeRunResult runResult = recipeExecutor.run(recipe.get(), javaRoot);
        for (FileRecipe.FileChange change : runResult.changes()) {
            allChanges.add(new ChangeRecord(
                    step.id() + "-" + String.format("%04d", changeCounter.incrementAndGet()),
                    step.recipe(),
                    step.category(),
                    change.relativePath(),
                    List.of(),
                    truncate(change.before()),
                    truncate(change.after()),
                    step.reason(),
                    step.evidence(),
                    "LOW",
                    true,
                    AnalyzeEngine.VERSION,
                    true));
        }

        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED", runResult.message());
    }

    private String truncate(String text) {
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 497) + "...";
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
    }
}
