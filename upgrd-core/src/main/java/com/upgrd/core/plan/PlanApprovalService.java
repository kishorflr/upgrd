package com.upgrd.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ApprovedPlan;
import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.StepApproval;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlanApprovalService {

    public static final String APPROVED_PLAN_FILE = "approved-plan.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public ApprovedPlan createDefault(UpgradePlan plan, Path sourceRoot) {
        return createDefault(plan, sourceRoot, true, false, false);
    }

    public ApprovedPlan createDefault(
            UpgradePlan plan,
            Path sourceRoot,
            boolean approveMandatory,
            boolean approveRecommended,
            boolean approveOptional) {
        List<StepApproval> approvals = new ArrayList<>();
        for (UpgradeStep step : plan.steps()) {
            boolean approved = defaultApproved(step, approveMandatory, approveRecommended, approveOptional);
            approvals.add(new StepApproval(
                    step.id(),
                    approved,
                    step.classification(),
                    step.mode(),
                    defaultNote(step, approved)));
        }
        return new ApprovedPlan(
                AnalyzeEngine.VERSION,
                Instant.now(),
                plan.upgrdVersion(),
                sourceRoot.toAbsolutePath().normalize().toString(),
                List.copyOf(approvals));
    }

    public ApprovedPlan applyOverrides(
            ApprovedPlan base,
            Set<String> approveSteps,
            Set<String> rejectSteps) {
        List<StepApproval> updated = new ArrayList<>();
        for (StepApproval entry : base.steps()) {
            boolean approved = entry.approved();
            String note = entry.note();
            if (approveSteps.contains(entry.stepId())) {
                approved = entry.mode() != StepMode.ADVISORY;
                note = entry.mode() == StepMode.ADVISORY
                        ? "Advisory — cannot auto-apply"
                        : "Explicitly approved";
            }
            if (rejectSteps.contains(entry.stepId())) {
                approved = false;
                note = "Explicitly rejected";
            }
            updated.add(new StepApproval(
                    entry.stepId(), approved, entry.classification(), entry.mode(), note));
        }
        return new ApprovedPlan(
                base.upgrdVersion(),
                Instant.now(),
                base.planUpgrdVersion(),
                base.sourceRoot(),
                List.copyOf(updated));
    }

    public void validate(ApprovedPlan approval, UpgradePlan plan) throws IOException {
        Set<String> planIds = new HashSet<>();
        for (UpgradeStep step : plan.steps()) {
            planIds.add(step.id());
        }
        Set<String> approvalIds = new HashSet<>();
        for (StepApproval entry : approval.steps()) {
            if (!approvalIds.add(entry.stepId())) {
                throw new IOException("Duplicate approval entry: " + entry.stepId());
            }
            if (!planIds.contains(entry.stepId())) {
                throw new IOException("Approval references unknown step: " + entry.stepId());
            }
        }
        for (String stepId : planIds) {
            if (!approvalIds.contains(stepId)) {
                throw new IOException("Missing approval for step: " + stepId);
            }
        }
        if (approval.approvedCount() == 0) {
            throw new IOException("No steps approved — run `plan approve` or approve steps in the UI");
        }
    }

    public Path write(ApprovedPlan approval, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve(APPROVED_PLAN_FILE);
        mapper.writeValue(file.toFile(), approval);
        return file;
    }

    public ApprovedPlan load(Path file) throws IOException {
        return mapper.readValue(file.toFile(), ApprovedPlan.class);
    }

    public ApprovedPlan loadFromOutput(Path outputDir) throws IOException {
        Path file = outputDir.resolve(APPROVED_PLAN_FILE);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Approved plan not found: " + file
                    + " — run `upgrd plan approve` or save approvals in the audit UI");
        }
        return load(file);
    }

    private boolean defaultApproved(
            UpgradeStep step,
            boolean approveMandatory,
            boolean approveRecommended,
            boolean approveOptional) {
        if (step.mode() == StepMode.ADVISORY) {
            return false;
        }
        return switch (step.classification()) {
            case MANDATORY -> approveMandatory;
            case RECOMMENDED -> approveRecommended;
            case OPTIONAL -> approveOptional;
            case REWRITE_REQUIRED -> false;
        };
    }

    private String defaultNote(UpgradeStep step, boolean approved) {
        if (step.mode() == StepMode.ADVISORY) {
            return "Advisory — manual refactor required";
        }
        if (approved) {
            return "Default approved (" + step.classification() + ")";
        }
        return "Default pending review (" + step.classification() + ")";
    }
}
