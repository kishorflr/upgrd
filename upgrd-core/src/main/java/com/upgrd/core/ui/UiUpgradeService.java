package com.upgrd.core.ui;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.model.ApprovedPlan;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.ApplyStepResult;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.model.WarConflictPolicy;
import com.upgrd.core.plan.PlanApprovalService;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.verify.VerifyEngine;
import com.upgrd.core.verify.VerifyEngine.VerifyOptions;
import com.upgrd.core.verify.VerifyEngine.VerifyResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * UI-triggered apply and verify — same engines as the CLI, localhost-only.
 */
public final class UiUpgradeService {

    private final ApplyEngine applyEngine = new ApplyEngine();
    private final PlanApprovalService approvalService = new PlanApprovalService();
    private final UpgradePlanner planner = new UpgradePlanner();
    private final ReportWriter reportWriter = new ReportWriter();
    private final WorkspaceStore workspaceStore = new WorkspaceStore();
    private final VerifyEngine verifyEngine = new VerifyEngine();

    public ApplyResult applyApproved(Path outputDir, String warPolicyName) throws Exception {
        Path planFile = outputDir.resolve("upgrade-plan.json");
        if (!Files.isRegularFile(planFile)) {
            throw new IOException("upgrade-plan.json not found — run plan upgrade first");
        }

        UpgradePlan plan = ensureApplyReadyPlan(planFile, outputDir);
        ApprovedPlan approval = approvalService.loadFromOutput(outputDir);
        approvalService.validate(approval, plan);

        Path source = resolveSourceRoot(outputDir, approval);
        WarConflictPolicy policy = parseWarPolicy(warPolicyName);
        var warOptions = reportWriter.resolveWarApplyOptions(outputDir, null, policy);

        ApplyReport report = applyEngine.apply(plan, source, outputDir, approval, warOptions);

        List<String> advisoryWarnings = new ArrayList<>();
        for (UpgradeStep step : plan.steps()) {
            if (step.mode() == StepMode.ADVISORY) {
                advisoryWarnings.add(step.description());
            }
        }

        long applied = report.steps().stream().filter(s -> "APPLIED".equals(s.status())).count();
        long skipped = report.steps().stream().filter(s -> "NOT_APPROVED".equals(s.status())).count();

        return new ApplyResult(
                true,
                approval.approvedCount(),
                applied,
                skipped,
                report.migratedRoot(),
                advisoryWarnings,
                summarizeApplySteps(report.steps()));
    }

    public VerifyBuildResult verifyBuild(Path outputDir, boolean securityScan) throws Exception {
        VerifyResult result = verifyEngine.verify(new VerifyOptions(
                outputDir,
                securityScan,
                false,
                false,
                false));

        String logTail = tailLines(result.log(), 40);
        return new VerifyBuildResult(
                result.passed(),
                result.exitCode(),
                result.reportPath().toString(),
                result.report().command(),
                logTail,
                result.report().summaryLines());
    }

    private UpgradePlan ensureApplyReadyPlan(Path planFile, Path outputDir) throws IOException {
        UpgradePlan plan = applyEngine.loadPlan(planFile);
        if (!plan.dryRun()) {
            return plan;
        }
        UpgradePlan applyReady = new UpgradePlan(
                plan.upgrdVersion(),
                false,
                plan.targetJava(),
                plan.productionServer(),
                plan.localServer(),
                plan.profile(),
                plan.steps());
        planner.writePlan(applyReady, outputDir);
        return applyReady;
    }

    private Path resolveSourceRoot(Path outputDir, ApprovedPlan approval) throws IOException {
        if (approval.sourceRoot() != null && !approval.sourceRoot().isBlank()) {
            Path fromApproval = Path.of(approval.sourceRoot());
            if (Files.isDirectory(fromApproval)) {
                return fromApproval;
            }
        }
        var workspace = workspaceStore.load(outputDir);
        if (workspace != null && workspace.sourceRoot() != null && !workspace.sourceRoot().isBlank()) {
            Path fromWorkspace = Path.of(workspace.sourceRoot());
            if (Files.isDirectory(fromWorkspace)) {
                return fromWorkspace;
            }
            throw new IOException("Source root not found: " + fromWorkspace);
        }
        throw new IOException("Source root unknown — set source path when starting the UI "
                + "(upgrd run --serve-ui --source ./app --war ./app.war) or save workspace in Coverage tab");
    }

    private WarConflictPolicy parseWarPolicy(String value) {
        if (value == null || value.isBlank()) {
            return WarConflictPolicy.WAR_WINS;
        }
        return switch (value.toLowerCase().replace('_', '-')) {
            case "war-wins" -> WarConflictPolicy.WAR_WINS;
            case "source-wins" -> WarConflictPolicy.SOURCE_WINS;
            case "mark-conflict" -> WarConflictPolicy.MARK_CONFLICT;
            default -> throw new IllegalArgumentException("Unknown war policy: " + value);
        };
    }

    private List<ApplyStepSummary> summarizeApplySteps(List<ApplyStepResult> steps) {
        return steps.stream()
                .map(s -> new ApplyStepSummary(s.stepId(), s.status(), s.message()))
                .toList();
    }

    private String tailLines(String log, int maxLines) {
        if (log == null || log.isBlank()) {
            return "";
        }
        String[] lines = log.split("\n");
        int start = Math.max(0, lines.length - maxLines);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
    }

    public record ApplyResult(
            boolean success,
            long approvedCount,
            long appliedCount,
            long skippedCount,
            String migratedRoot,
            List<String> advisoryWarnings,
            List<ApplyStepSummary> steps) {
    }

    public record ApplyStepSummary(String stepId, String status, String message) {
    }

    public record VerifyBuildResult(
            boolean passed,
            int exitCode,
            String reportPath,
            String command,
            String logTail,
            List<String> summaryLines) {
    }
}
