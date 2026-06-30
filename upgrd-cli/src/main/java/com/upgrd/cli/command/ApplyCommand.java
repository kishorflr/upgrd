package com.upgrd.cli.command;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.model.ApprovedPlan;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.plan.PlanApprovalService;
import com.upgrd.recipes.RecipeCatalog;
import com.upgrd.recipes.RecipeDefinition;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "apply",
        description = "Apply an upgrade plan (source migration + recipe execution)")
public final class ApplyCommand implements Callable<Integer> {

    @Option(names = "--plan", required = true, description = "Path to upgrade-plan.json")
    private Path plan;

    @Option(names = "--source", required = true, description = "Java project source root")
    private Path source;

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Option(names = "--approval", description = "Path to approved-plan.json (default: output/approved-plan.json if present)")
    private Path approvalFile;

    @Option(names = "--skip-approval", defaultValue = "false",
            description = "Apply all automated steps without approved-plan.json")
    private boolean skipApproval;

    @Override
    public Integer call() throws Exception {
        if (!Files.isRegularFile(plan)) {
            throw new IllegalArgumentException("Plan file not found: " + plan);
        }

        ApplyEngine engine = new ApplyEngine();
        UpgradePlan upgradePlan = engine.loadPlan(plan);
        RecipeCatalog catalog = new RecipeCatalog();
        ApprovedPlan approval = resolveApproval(upgradePlan);

        System.out.printf("UpGrd apply.%n");
        System.out.printf("  Plan steps: %d | dry-run: %s%n", upgradePlan.steps().size(), upgradePlan.dryRun());
        if (approval != null) {
            System.out.printf("  Approved steps: %d / %d%n",
                    approval.approvedCount(), approval.steps().size());
        }
        upgradePlan.steps().forEach(step -> {
            String status = catalog.findByStepId(step.id())
                    .map(RecipeDefinition::implemented)
                    .map(implemented -> implemented ? "ready" : "catalog-only")
                    .orElse("unknown");
            String gate = approval == null || approval.isApproved(step.id()) ? "" : " [not approved]";
            System.out.printf("    - [%s] %s (%s) — %s%s%n",
                    step.category(), step.description(), step.recipe(), status, gate);
        });

        ApplyReport report = engine.apply(upgradePlan, source, output, approval);
        Path reportFile = output.resolve("apply-report.json");

        System.out.printf("  Migrated layout: %s%n", report.migratedRoot());
        System.out.printf("  Apply report: %s%n", reportFile.toAbsolutePath());
        return 0;
    }

    private ApprovedPlan resolveApproval(UpgradePlan plan) throws Exception {
        if (skipApproval) {
            return null;
        }
        Path file = approvalFile != null ? approvalFile : output.resolve(PlanApprovalService.APPROVED_PLAN_FILE);
        if (!Files.isRegularFile(file)) {
            if (approvalFile != null) {
                throw new IllegalArgumentException("Approval file not found: " + file);
            }
            System.out.println("  No approved-plan.json — applying all automated steps (use plan approve to gate)");
            return null;
        }
        PlanApprovalService service = new PlanApprovalService();
        ApprovedPlan approval = service.load(file);
        service.validate(approval, plan);
        return approval;
    }
}
