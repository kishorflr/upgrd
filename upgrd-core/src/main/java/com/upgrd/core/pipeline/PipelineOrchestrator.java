package com.upgrd.core.pipeline;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.model.ApprovedPlan;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.AnalysisReport;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradePreviewReport;
import com.upgrd.core.openrewrite.OpenRewriteRunner;
import com.upgrd.core.plan.PlanApprovalService;
import com.upgrd.core.plan.PlanPreviewEngine;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityAnalyzer;
import com.upgrd.core.verify.VerifyEngine;
import com.upgrd.core.verify.VerifyEngine.VerifyOptions;
import com.upgrd.core.verify.VerifyEngine.VerifyResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end edge-local pipeline: analyze → plan → preview → (optional) apply → verify.
 */
public final class PipelineOrchestrator {

    public PipelineResult run(PipelineRequest request) throws Exception {
        List<String> phases = new ArrayList<>();

        AnalyzeEngine analyzeEngine = new AnalyzeEngine();
        AnalysisReport analysis = analyzeEngine.analyze(new AnalysisInput(
                request.source(),
                request.war(),
                request.logs(),
                request.output(),
                request.profile()));
        analyzeEngine.writeReport(analysis, request.output());
        phases.add("analyze");

        var discovery = analysis.discovery();
        var security = new SecurityAnalyzer().analyze(request.source(), discovery);
        new ReportWriter().writeSecurityReport(security, request.output());

        UpgradePlanner planner = new UpgradePlanner();
        UpgradePlan plan = planner.plan(
                discovery,
                request.targetJava(),
                request.productionServer(),
                false,
                security);
        Path planFile = planner.writePlan(plan, request.output());
        new ReportWriter().writeChangeLedger(
                new ReportWriter().previewFromPlan(plan, request.source()),
                request.output());
        phases.add("plan");

        UpgradePreviewReport preview = new PlanPreviewEngine().preview(plan, request.source());
        Path previewFile = new ReportWriter().writeUpgradePreviewReport(preview, request.output());
        phases.add("preview");

        if (!request.confirmApply()) {
            return new PipelineResult(true, phases, planFile, previewFile, null, null, null);
        }

        PlanApprovalService approvalService = new PlanApprovalService();
        ApprovedPlan approval;
        if (request.autoApproveMandatory()) {
            approval = approvalService.createDefault(plan, request.source(), true, false, false);
            approvalService.write(approval, request.output());
        } else {
            approval = approvalService.loadFromOutput(request.output());
        }
        approvalService.validate(approval, plan);

        ApplyEngine applyEngine = new ApplyEngine();
        ApplyReport applyReport = applyEngine.apply(plan, request.source(), request.output(), approval);
        applyEngine.writeReport(applyReport, request.output());
        phases.add("apply");

        OpenRewriteRunner.RewriteResult rewriteResult = null;
        if (request.runRewrite() && !request.rewriteAfterVerify()) {
            rewriteResult = runRewrite(request);
            phases.add("rewrite");
            if (!rewriteResult.success()) {
                return new PipelineResult(false, phases, planFile, previewFile, applyReport, null, rewriteResult);
            }
        }

        boolean runWildflyHttp = request.wildflyHttp()
                || (request.wildflyHttpWhenWebProfile()
                && discovery.profile() == ProjectProfile.LEGACY_WEB);

        VerifyResult verifyResult = null;
        if (request.runVerify()) {
            verifyResult = new VerifyEngine().verify(new VerifyOptions(
                    request.output(),
                    request.securityScan(),
                    request.wildflySmoke(),
                    request.wildflyDeploy(),
                    runWildflyHttp));
            phases.add("verify");
            if (!verifyResult.passed()) {
                return new PipelineResult(false, phases, planFile, previewFile, applyReport, verifyResult, rewriteResult);
            }
        }

        if (request.runRewrite() && request.rewriteAfterVerify()) {
            rewriteResult = runRewrite(request);
            phases.add("rewrite");
            if (!rewriteResult.success()) {
                return new PipelineResult(false, phases, planFile, previewFile, applyReport, verifyResult, rewriteResult);
            }
        }

        return new PipelineResult(true, phases, planFile, previewFile, applyReport, verifyResult, rewriteResult);
    }

    private OpenRewriteRunner.RewriteResult runRewrite(PipelineRequest request)
            throws Exception {
        return new OpenRewriteRunner().run(
                request.output(),
                request.rewriteDryRun(),
                false,
                request.rewriteRecipe());
    }

    public record PipelineRequest(
            Path source,
            Path war,
            List<Path> logs,
            Path output,
            ProjectProfile profile,
            String targetJava,
            String productionServer,
            boolean confirmApply,
            boolean autoApproveMandatory,
            boolean runVerify,
            boolean securityScan,
            boolean wildflySmoke,
            boolean wildflyDeploy,
            boolean wildflyHttp,
            boolean wildflyHttpWhenWebProfile,
            boolean runRewrite,
            boolean rewriteDryRun,
            String rewriteRecipe,
            boolean rewriteAfterVerify) {
    }

    public record PipelineResult(
            boolean success,
            List<String> completedPhases,
            Path planFile,
            Path previewReportFile,
            ApplyReport applyReport,
            VerifyResult verifyResult,
            OpenRewriteRunner.RewriteResult rewriteResult) {
    }
}
