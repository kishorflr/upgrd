package com.upgrd.core.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.documentation.ApplicationDocumenter;
import com.upgrd.core.documentation.DocumentationWriter;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.ApplyStepResult;
import com.upgrd.core.model.ChangeLedger;
import com.upgrd.core.model.ChangeRecord;
import com.upgrd.core.model.SecurityReport;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.model.UsageReport;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityReportMerger;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ApplyEngine {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final ReportWriter reportWriter = new ReportWriter();
    private final ApplicationDocumenter applicationDocumenter = new ApplicationDocumenter();
    private final DocumentationWriter documentationWriter = new DocumentationWriter();
    private final SecurityReportMerger securityReportMerger = new SecurityReportMerger();
    private final SourceMigrator sourceMigrator = new SourceMigrator();
    private final MavenScaffolder mavenScaffolder = new MavenScaffolder();
    private final AutomationReadinessScaffolder automationScaffolder = new AutomationReadinessScaffolder();
    private final DeployProfileScaffolder deployProfileScaffolder = new DeployProfileScaffolder();
    private final OpenRewriteScaffolder openRewriteScaffolder = new OpenRewriteScaffolder();
    private final SmokeTestGenerator smokeTestGenerator = new SmokeTestGenerator();
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
        Set<String> appliedRecipeIds = new HashSet<>();
        AtomicInteger changeCounter = new AtomicInteger();
        List<String> testEntryPoints = new ArrayList<>();

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
                    step, plan, sourceRoot, outputDir, migratedRoot, appWebRoot,
                    allChanges, changeCounter, testEntryPoints);
            results.add(stepResult);
            if ("APPLIED".equals(stepResult.status()) && step.recipe() != null) {
                appliedRecipeIds.add(step.recipe());
            }
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

        SecurityReport securityBefore = reportWriter.readSecurityReport(outputDir);
        if (securityBefore != null) {
            SecurityReport securityAfter = securityReportMerger.markRemediated(
                    securityBefore, appliedRecipeIds);
            reportWriter.writeSecurityReport(securityAfter, outputDir);
            updateDocumentation(outputDir, report, ledger, securityAfter);
        }

        return report;
    }

    private void updateDocumentation(
            Path outputDir,
            ApplyReport applyReport,
            ChangeLedger ledger,
            SecurityReport securityAfter) throws IOException {
        var existing = reportWriter.readDocumentation(outputDir);
        if (existing == null) {
            return;
        }
        var updated = applicationDocumenter.appendApplyPhase(existing, applyReport, ledger, securityAfter);
        documentationWriter.write(updated, outputDir);
    }

    private ApplyStepResult executeStep(
            UpgradeStep step,
            UpgradePlan plan,
            Path sourceRoot,
            Path outputDir,
            Path migratedRoot,
            Path appWebRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter,
            List<String> testEntryPoints) throws IOException {
        return switch (step.id()) {
            case "convert-maven" -> runConvertMaven(step, plan, sourceRoot, migratedRoot, appWebRoot,
                    allChanges, changeCounter);
            case "wildfly-local" -> runWildflyLocal(step, plan, migratedRoot, allChanges, changeCounter);
            case "weblogic-adapters" -> runWeblogicAdapters(step, plan, migratedRoot, allChanges, changeCounter);
            case "security-verify" -> runSecurityVerifyScaffold(step, migratedRoot, allChanges, changeCounter);
            case "openrewrite-scaffold" -> runOpenRewriteScaffold(step, plan, migratedRoot, allChanges, changeCounter);
            case "test-scaffold" -> runTestScaffold(step, outputDir, appWebRoot, allChanges, changeCounter, testEntryPoints);
            case "automation-ready" -> runAutomationReady(step, plan, migratedRoot, testEntryPoints, allChanges, changeCounter);
            default -> runRecipe(step, appWebRoot, allChanges, changeCounter);
        };
    }

    private ApplyStepResult runTestScaffold(
            UpgradeStep step,
            Path outputDir,
            Path appWebRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter,
            List<String> testEntryPoints) throws IOException {
        if (!Files.isDirectory(appWebRoot)) {
            return new ApplyStepResult(step.id(), step.recipe(), "SKIPPED",
                    "No migrated app-web — run convert-maven first");
        }
        UsageReport usage = reportWriter.readUsageReport(outputDir);
        SmokeTestGenerator.GenerationResult result = smokeTestGenerator.generate(appWebRoot, usage);
        testEntryPoints.addAll(result.entryPoints());

        for (String file : result.generatedFiles()) {
            allChanges.add(new ChangeRecord(
                    step.id() + "-" + String.format("%04d", changeCounter.incrementAndGet()),
                    step.recipe(),
                    step.category(),
                    file,
                    List.of(),
                    "",
                    "(generated smoke test)",
                    step.reason(),
                    result.entryPoints(),
                    "LOW",
                    true,
                    AnalyzeEngine.VERSION,
                    true));
        }

        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Generated " + result.generatedFiles().size() + " JUnit 5 smoke test(s) in app-web/src/test/java");
    }

    private ApplyStepResult runAutomationReady(
            UpgradeStep step,
            UpgradePlan plan,
            Path migratedRoot,
            List<String> testEntryPoints,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        List<String> artifacts = automationScaffolder.scaffold(migratedRoot, plan, testEntryPoints);

        allChanges.add(new ChangeRecord(
                step.id() + "-" + String.format("%04d", changeCounter.incrementAndGet()),
                step.recipe(),
                step.category(),
                "migrated/",
                List.of(),
                "",
                "Automation metadata: " + String.join(", ", artifacts),
                step.reason(),
                artifacts,
                "LOW",
                true,
                AnalyzeEngine.VERSION,
                true));

        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Embedded AGENTS.md, upgrd-analysis.json, and test layout for automation/AI analysis");
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
                "Maven multi-module layout with app-web/ and JUnit 5 test deps",
                step.reason(),
                List.copyOf(copied.stream().limit(10).toList()),
                "LOW",
                true,
                AnalyzeEngine.VERSION,
                true));

        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Copied " + copied.size() + " file(s) and generated Maven POMs with test infrastructure");
    }

    private ApplyStepResult runWildflyLocal(
            UpgradeStep step,
            UpgradePlan plan,
            Path migratedRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        List<String> artifacts = deployProfileScaffolder.scaffoldWildFly(migratedRoot, "app-web");
        recordDeployChange(step, migratedRoot, allChanges, changeCounter, artifacts);
        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Generated WildFly local deploy profile (" + artifacts.size() + " file(s))");
    }

    private ApplyStepResult runWeblogicAdapters(
            UpgradeStep step,
            UpgradePlan plan,
            Path migratedRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        List<String> artifacts = deployProfileScaffolder.scaffoldWebLogic(migratedRoot, plan.productionServer());
        recordDeployChange(step, migratedRoot, allChanges, changeCounter, artifacts);
        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Generated WebLogic production deploy overlays (" + artifacts.size() + " file(s))");
    }

    private ApplyStepResult runOpenRewriteScaffold(
            UpgradeStep step,
            UpgradePlan plan,
            Path migratedRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        List<String> artifacts = openRewriteScaffolder.scaffold(migratedRoot, plan.targetJava());
        allChanges.add(new ChangeRecord(
                step.id() + "-" + String.format("%04d", changeCounter.incrementAndGet()),
                step.recipe(),
                step.category(),
                "migrated/.upgrd/",
                List.of(),
                "",
                "OpenRewrite scaffold: " + String.join(", ", artifacts),
                step.reason(),
                List.copyOf(artifacts),
                "LOW",
                true,
                AnalyzeEngine.VERSION,
                true));
        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Scaffolded OpenRewrite config for optional AST migrations");
    }

    private ApplyStepResult runSecurityVerifyScaffold(
            UpgradeStep step,
            Path migratedRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) {
        allChanges.add(new ChangeRecord(
                step.id() + "-" + String.format("%04d", changeCounter.incrementAndGet()),
                step.recipe(),
                step.category(),
                "migrated/pom.xml",
                List.of(),
                "",
                "security-verify Maven profile (SpotBugs + OWASP Dependency-Check)",
                step.reason(),
                List.of("-Psecurity-verify"),
                "LOW",
                true,
                AnalyzeEngine.VERSION,
                true));
        return new ApplyStepResult(step.id(), step.recipe(), "APPLIED",
                "Security verify profile available — run: mvn verify -Psecurity-verify");
    }

    private void recordDeployChange(
            UpgradeStep step,
            Path migratedRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter,
            List<String> artifacts) {
        allChanges.add(new ChangeRecord(
                step.id() + "-" + String.format("%04d", changeCounter.incrementAndGet()),
                step.recipe(),
                step.category(),
                migratedRoot.resolve("deploy").toString(),
                List.of(),
                "",
                "Deploy artifacts: " + String.join(", ", artifacts),
                step.reason(),
                List.copyOf(artifacts),
                "LOW",
                true,
                AnalyzeEngine.VERSION,
                true));
    }

    private ApplyStepResult runRecipe(
            UpgradeStep step,
            Path appWebRoot,
            List<ChangeRecord> allChanges,
            AtomicInteger changeCounter) throws IOException {
        RecipeDefinition definition = recipeCatalog.findByStepId(step.id()).orElse(null);
        if (definition == null || !definition.implemented()) {
            if ("security".equals(step.category()) && recipeRegistry.resolve(step.recipe()).isPresent()) {
                definition = new RecipeDefinition(step.id(), step.recipe(), step.description(), true);
            } else {
                return new ApplyStepResult(step.id(), step.recipe(), "PENDING",
                        "Recipe execution not yet implemented");
            }
        }

        var recipe = recipeRegistry.resolve(step.recipe());
        if (recipe.isEmpty()) {
            return new ApplyStepResult(step.id(), step.recipe(), "SKIPPED",
                    "No executable recipe registered for " + step.recipe());
        }

        if (!Files.isDirectory(appWebRoot)) {
            return new ApplyStepResult(step.id(), step.recipe(), "SKIPPED",
                    "No migrated sources yet — run convert-maven first");
        }

        RecipeExecutor.RecipeRunResult runResult = recipeExecutor.runOnProject(recipe.get(), appWebRoot);
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
                    step.category().equals("security") ? "MEDIUM" : "LOW",
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
