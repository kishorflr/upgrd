package com.upgrd.cli.command;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.recipes.RecipeCatalog;
import com.upgrd.recipes.RecipeDefinition;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "apply",
        description = "Apply an upgrade plan (M2 scaffold: layout + apply report)")
public final class ApplyCommand implements Callable<Integer> {

    @Option(names = "--plan", required = true, description = "Path to upgrade-plan.json")
    private Path plan;

    @Option(names = "--source", required = true, description = "Java project source root")
    private Path source;

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        if (!Files.isRegularFile(plan)) {
            throw new IllegalArgumentException("Plan file not found: " + plan);
        }

        ApplyEngine engine = new ApplyEngine();
        UpgradePlan upgradePlan = engine.loadPlan(plan);
        RecipeCatalog catalog = new RecipeCatalog();

        System.out.printf("UpGrd apply (M2 scaffold).%n");
        System.out.printf("  Plan steps: %d | dry-run: %s%n", upgradePlan.steps().size(), upgradePlan.dryRun());
        upgradePlan.steps().forEach(step -> {
            String status = catalog.findByStepId(step.id())
                    .map(RecipeDefinition::implemented)
                    .map(implemented -> implemented ? "ready" : "catalog-only")
                    .orElse("unknown");
            System.out.printf("    - [%s] %s (%s) — %s%n",
                    step.category(), step.description(), step.recipe(), status);
        });

        ApplyReport report = engine.apply(upgradePlan, source, output);
        Path reportFile = output.resolve("apply-report.json");

        System.out.printf("  Migrated layout: %s%n", report.migratedRoot());
        System.out.printf("  Apply report: %s%n", reportFile.toAbsolutePath());
        return 0;
    }
}
