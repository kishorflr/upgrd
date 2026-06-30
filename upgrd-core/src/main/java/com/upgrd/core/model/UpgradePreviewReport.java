package com.upgrd.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Plan-time preview of file-level changes with before/after snippets for review.
 */
public record UpgradePreviewReport(
        String version,
        Instant generatedAt,
        String sourceRoot,
        ProjectProfile profile,
        int automatedSteps,
        int advisorySteps,
        int previewedFileChanges,
        List<PreviewStepSummary> steps,
        List<ChangeRecord> changes) {

    public record PreviewStepSummary(
            String stepId,
            String description,
            String recipe,
            StepMode mode,
            ChangeClassification classification,
            int previewedFileChanges,
            String previewNote) {
    }
}
