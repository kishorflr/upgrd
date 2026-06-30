package com.upgrd.cli.command;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.model.ApprovedPlan;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.plan.PlanApprovalService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "approve",
        description = "Create or update approved-plan.json for gated apply")
public final class PlanApproveCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Option(names = "--plan", description = "Path to upgrade-plan.json (default: output/upgrade-plan.json)")
    private Path planFile;

    @Option(names = "--source", description = "Source root recorded in approval (optional)")
    private Path source;

    @Option(names = "--approve-mandatory", defaultValue = "true", description = "Approve MANDATORY automated steps")
    private boolean approveMandatory;

    @Option(names = "--approve-recommended", defaultValue = "false",
            description = "Approve RECOMMENDED automated steps by default")
    private boolean approveRecommended;

    @Option(names = "--approve-optional", defaultValue = "false",
            description = "Approve OPTIONAL automated steps by default")
    private boolean approveOptional;

    @Option(names = "--approve-steps", split = ",", description = "Explicitly approve step IDs")
    private Set<String> approveSteps = new HashSet<>();

    @Option(names = "--reject-steps", split = ",", description = "Explicitly reject step IDs")
    private Set<String> rejectSteps = new HashSet<>();

    @Override
    public Integer call() throws Exception {
        Path planPath = planFile != null ? planFile : output.resolve("upgrade-plan.json");
        if (!Files.isRegularFile(planPath)) {
            throw new IllegalArgumentException("Plan file not found: " + planPath);
        }

        UpgradePlan plan = new ApplyEngine().loadPlan(planPath);
        Path sourceRoot = source != null ? source : Path.of(".");
        PlanApprovalService service = new PlanApprovalService();

        ApprovedPlan approval = service.createDefault(
                plan, sourceRoot, approveMandatory, approveRecommended, approveOptional);
        if (!approveSteps.isEmpty() || !rejectSteps.isEmpty()) {
            approval = service.applyOverrides(approval, approveSteps, rejectSteps);
        }
        service.validate(approval, plan);
        Path file = service.write(approval, output);

        System.out.printf("UpGrd plan approval.%n");
        System.out.printf("  Approved steps: %d / %d%n", approval.approvedCount(), approval.steps().size());
        approval.steps().forEach(s ->
                System.out.printf("    - [%s] %s — %s%n",
                        s.approved() ? "APPROVED" : "rejected",
                        s.stepId(),
                        s.note()));
        System.out.printf("  Approval file: %s%n", file.toAbsolutePath());
        System.out.println("  Apply: upgrd apply --plan ... --approval " + file);
        return 0;
    }
}
