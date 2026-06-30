package com.upgrd.core.plan;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ChangeRecord;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradePreviewReport;
import com.upgrd.core.model.UpgradeStep;
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

/**
 * Dry-run preview of upgrade plan file recipes against source — no writes, real before/after diffs.
 */
public final class PlanPreviewEngine {

    private static final java.util.Set<String> SCAFFOLD_ONLY = java.util.Set.of(
            "convert-maven", "wildfly-local", "weblogic-adapters", "security-verify",
            "openrewrite-scaffold", "openrewrite-dry-run", "thymeleaf-wiring",
            "test-scaffold", "automation-ready");

    private final RecipeRegistry recipeRegistry = new RecipeRegistry();
    private final RecipeExecutor recipeExecutor = new RecipeExecutor();
    private final RecipeCatalog recipeCatalog = new RecipeCatalog();

    public UpgradePreviewReport preview(UpgradePlan plan, Path sourceRoot) throws IOException {
        List<UpgradePreviewReport.PreviewStepSummary> stepSummaries = new ArrayList<>();
        List<ChangeRecord> allChanges = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        int automated = 0;
        int advisory = 0;

        for (UpgradeStep step : plan.steps()) {
            if (step.mode() == StepMode.ADVISORY) {
                advisory++;
                stepSummaries.add(summary(step, 0,
                        "Advisory — manual review required; no automatic file preview"));
                continue;
            }
            automated++;

            if (SCAFFOLD_ONLY.contains(step.id())) {
                stepSummaries.add(summary(step, 0,
                        "Scaffold/layout step — previewed at plan level; diffs appear after apply"));
                allChanges.add(scaffoldPreview(step, sourceRoot, counter));
                continue;
            }

            var preview = previewRecipeStep(step, sourceRoot);
            stepSummaries.add(summary(step, preview.changes().size(), preview.message()));
            for (var change : preview.changes()) {
                allChanges.add(toChangeRecord(step, change, sourceRoot, counter));
            }
        }

        return new UpgradePreviewReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                sourceRoot.toAbsolutePath().normalize().toString(),
                plan.profile(),
                automated,
                advisory,
                allChanges.size(),
                List.copyOf(stepSummaries),
                List.copyOf(allChanges));
    }

    private RecipeExecutor.RecipeRunResult previewRecipeStep(UpgradeStep step, Path sourceRoot)
            throws IOException {
        RecipeDefinition definition = recipeCatalog.findByStepId(step.id()).orElse(null);
        if (definition == null || !definition.implemented()) {
            return new RecipeExecutor.RecipeRunResult(List.of(), 0, "No preview — recipe not implemented");
        }
        var recipe = recipeRegistry.resolve(step.recipe());
        if (recipe.isEmpty()) {
            return new RecipeExecutor.RecipeRunResult(List.of(), 0, "No preview — recipe not registered");
        }
        Path previewRoot = resolvePreviewRoot(sourceRoot);
        if (!Files.isDirectory(previewRoot)) {
            return new RecipeExecutor.RecipeRunResult(List.of(), 0,
                    "No preview root — run convert-maven during apply first");
        }
        return recipeExecutor.previewOnProject(recipe.get(), previewRoot);
    }

    private Path resolvePreviewRoot(Path sourceRoot) throws IOException {
        if (Files.isDirectory(sourceRoot.resolve("src/main/java"))) {
            return sourceRoot;
        }
        Path nested = sourceRoot.resolve("src");
        if (Files.isDirectory(nested)) {
            return sourceRoot;
        }
        return sourceRoot;
    }

    private ChangeRecord scaffoldPreview(UpgradeStep step, Path sourceRoot, AtomicInteger counter) {
        return new ChangeRecord(
                step.id() + "-preview-" + String.format("%04d", counter.incrementAndGet()),
                step.recipe(),
                step.category(),
                sourceRoot.toAbsolutePath().normalize().toString(),
                List.of(),
                "(current layout)",
                "(after " + step.id() + ")",
                step.reason(),
                step.evidence(),
                classificationRisk(step.classification()),
                true,
                AnalyzeEngine.VERSION,
                step.mode() == StepMode.AUTOMATED,
                step.classification());
    }

    private ChangeRecord toChangeRecord(
            UpgradeStep step,
            com.upgrd.recipes.FileRecipe.FileChange change,
            Path sourceRoot,
            AtomicInteger counter) {
        return new ChangeRecord(
                step.id() + "-preview-" + String.format("%04d", counter.incrementAndGet()),
                step.recipe(),
                step.category(),
                change.relativePath(),
                List.of(),
                truncate(change.before()),
                truncate(change.after()),
                step.reason(),
                step.evidence(),
                classificationRisk(step.classification()),
                true,
                AnalyzeEngine.VERSION,
                true,
                step.classification());
    }

    private UpgradePreviewReport.PreviewStepSummary summary(
            UpgradeStep step, int fileChanges, String note) {
        return new UpgradePreviewReport.PreviewStepSummary(
                step.id(),
                step.description(),
                step.recipe(),
                step.mode(),
                step.classification(),
                fileChanges,
                note);
    }

    private String classificationRisk(ChangeClassification classification) {
        return switch (classification) {
            case MANDATORY -> "HIGH";
            case RECOMMENDED -> "MEDIUM";
            case REWRITE_REQUIRED -> "HIGH";
            case OPTIONAL -> "LOW";
        };
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 800) {
            return text;
        }
        return text.substring(0, 797) + "...";
    }
}
