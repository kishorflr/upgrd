package com.upgrd.core.security;

import com.upgrd.core.model.SecurityFinding;
import com.upgrd.core.model.SecurityReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SecurityReportMerger {

    public SecurityReport markRemediated(SecurityReport report, Set<String> remediatedRecipeIds) {
        List<SecurityFinding> updated = new ArrayList<>();
        int remediated = 0;
        for (SecurityFinding finding : report.findings()) {
            boolean fixed = finding.autoFixable()
                    && finding.recipeId() != null
                    && remediatedRecipeIds.contains(finding.recipeId());
            if (fixed) {
                remediated++;
            }
            updated.add(new SecurityFinding(
                    finding.findingId(),
                    finding.severity(),
                    finding.category(),
                    finding.cveId(),
                    finding.file(),
                    finding.lineRange(),
                    finding.description(),
                    finding.remediation(),
                    finding.recipeId(),
                    finding.autoFixable(),
                    fixed || finding.remediated()));
        }
        int open = (int) updated.stream().filter(f -> !f.remediated()).count();
        return new SecurityReport(
                report.upgrdVersion(),
                report.generatedAt(),
                report.profile(),
                updated,
                remediated,
                open);
    }

    public List<String> recipeIdsForOpenFindings(SecurityReport report) {
        return report.findings().stream()
                .filter(f -> !f.remediated() && f.autoFixable() && f.recipeId() != null)
                .map(SecurityFinding::recipeId)
                .distinct()
                .collect(Collectors.toList());
    }
}
