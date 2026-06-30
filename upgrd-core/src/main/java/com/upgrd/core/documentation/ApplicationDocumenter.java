package com.upgrd.core.documentation;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AnalysisReport;
import com.upgrd.core.model.ApplicationDocumentation;
import com.upgrd.core.model.ApplyReport;
import com.upgrd.core.model.ChangeLedger;
import com.upgrd.core.model.DocumentationSection;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.SecurityReport;
import com.upgrd.core.model.TechnologyFingerprint;
import com.upgrd.core.model.UsageHit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds structured application documentation for future agent and human analysis.
 */
public final class ApplicationDocumenter {

    public ApplicationDocumentation documentAnalyzePhase(
            AnalysisReport analysis,
            SecurityReport security,
            String sourceRoot) {
        AtomicInteger counter = new AtomicInteger();
        List<DocumentationSection> sections = new ArrayList<>();
        ProjectDiscovery d = analysis.discovery();
        TechnologyFingerprint fp = d.fingerprint();

        sections.add(section(counter, "overview", "Application Overview", "analyze",
                """
                Profile: %s
                Build system: %s
                Java version hint: %s
                Production target: WebLogic 14c | Local verification: WildFly
                """.formatted(d.profile(), d.buildSystem(), d.javaVersionHint()),
                List.of("analysis-report.json")));

        sections.add(section(counter, "stack", "Technology Stack", "analyze",
                """
                Frameworks: %s
                Logging: %s
                Servlet API: %s
                Persistence: %s
                Web descriptors: %s
                """.formatted(
                        joinOrNone(fp.frameworks()),
                        fp.logging(),
                        fp.servletApi(),
                        fp.persistenceHint(),
                        joinOrNone(d.webInfDescriptors())),
                fp.evidence()));

        sections.add(section(counter, "inventory", "Code Inventory", "analyze",
                """
                Source classes: %d
                WAR classes: %d
                In both: %d
                Only in WAR: %d
                Only in source: %d
                Source roots: %s
                """.formatted(
                        analysis.sync().sourceClassCount(),
                        analysis.sync().warClassCount(),
                        analysis.sync().inBoth().size(),
                        analysis.sync().onlyInWar().size(),
                        analysis.sync().onlyInSource().size(),
                        joinOrNone(d.sourceRoots())),
                List.of("sync-report.json")));

        if (!analysis.usage().hits().isEmpty()) {
            StringBuilder hotPaths = new StringBuilder("Hot paths from logs (prioritize tests and review):\n");
            for (UsageHit hit : analysis.usage().hits().stream().limit(15).toList()) {
                hotPaths.append("  - ").append(hit.qualifiedName())
                        .append(" (").append(hit.hitCount()).append(" hits)\n");
            }
            sections.add(section(counter, "usage", "Runtime Hot Paths", "analyze",
                    hotPaths.toString(), List.of("usage-report.json")));
        }

        if (!analysis.designAdvisory().advisories().isEmpty()) {
            sections.add(section(counter, "design", "Design Notes", "analyze",
                    analysis.designAdvisory().advisories().size()
                            + " structural advisories recorded — see design-advisory.json for refactor candidates.",
                    List.of("design-advisory.json")));
        }

        if (!security.findings().isEmpty()) {
            StringBuilder sec = new StringBuilder("Security findings at analysis time:\n");
            for (var f : security.findings()) {
                sec.append("  - [").append(f.severity()).append("] ")
                        .append(f.category()).append(": ").append(f.description());
                if (f.cveId() != null) {
                    sec.append(" (").append(f.cveId()).append(")");
                }
                sec.append("\n");
            }
            sections.add(section(counter, "security", "Security Baseline", "analyze",
                    sec.toString(), List.of("security-report.json")));
        }

        sections.add(section(counter, "agent-guide", "Guide for Future Agents", "analyze",
                """
                This application was analyzed by UpGrd. To continue work:
                1. Read app-documentation.json and security-report.json in upgrd-out/
                2. Review change-ledger.json after apply for exact diffs and reasoning
                3. Respect ADVISORY steps in upgrade-plan.json — do not auto-apply structural refactors
                4. Hot paths in usage-report.json indicate production-critical code paths
                5. feature-usage-report.json lists OBSERVED vs UNOBSERVED features for regression planning
                6. All reports are edge-local JSON — no cloud dependency
                """,
                reportIndex()));

        return new ApplicationDocumentation(
                AnalyzeEngine.VERSION,
                Instant.now(),
                sourceRoot,
                d.profile(),
                "Legacy Java application documented during UpGrd analyze phase",
                List.copyOf(sections),
                reportIndex());
    }

    public ApplicationDocumentation appendApplyPhase(
            ApplicationDocumentation existing,
            ApplyReport applyReport,
            ChangeLedger ledger,
            SecurityReport securityAfterApply) {
        AtomicInteger counter = new AtomicInteger(existing.sections().size());
        List<DocumentationSection> sections = new ArrayList<>(existing.sections());

        StringBuilder applied = new StringBuilder("Upgrade steps executed:\n");
        applyReport.steps().forEach(step ->
                applied.append("  - [").append(step.status()).append("] ")
                        .append(step.stepId()).append(": ").append(step.message()).append("\n"));

        sections.add(section(counter, "upgrade-applied", "Upgrade Execution", "apply",
                applied.toString(), List.of("apply-report.json")));

        sections.add(section(counter, "changes", "Change Summary", "apply",
                ledger.changes().size() + " recorded changes with before/after in change-ledger.json",
                List.of("change-ledger.json")));

        sections.add(section(counter, "security-post", "Security After Upgrade", "apply",
                """
                Remediated: %d | Open: %d
                Review remaining open findings before production deploy.
                """.formatted(securityAfterApply.remediatedCount(), securityAfterApply.openCount()),
                List.of("security-report.json")));

        sections.add(section(counter, "agent-post", "Post-Upgrade Agent Notes", "apply",
                """
                Migrated sources live under migrated/app-web/
                Maven POMs with JUnit 5 + Surefire under migrated/
                Smoke tests: app-web/src/test/java/com/upgrd/smoke/
                Run tests: mvn -f migrated/pom.xml test
                Automation metadata: migrated/upgrd-analysis.json, migrated/AGENTS.md
                Server overlays: migrated/deploy/wildfly (local), migrated/deploy/weblogic (prod)
                Re-run `upgrd analyze` on migrated tree after manual edits to refresh documentation.
                """,
                List.of("migrated/pom.xml", "migrated/AGENTS.md", "migrated/upgrd-analysis.json")));

        return new ApplicationDocumentation(
                existing.upgrdVersion(),
                Instant.now(),
                existing.sourceRoot(),
                existing.profile(),
                "Application documented through UpGrd analyze and apply phases",
                List.copyOf(sections),
                existing.reportIndex());
    }

    private DocumentationSection section(
            AtomicInteger counter,
            String category,
            String title,
            String phase,
            String content,
            List<String> relatedFiles) {
        return new DocumentationSection(
                "doc-" + String.format("%04d", counter.incrementAndGet()),
                title,
                category,
                phase,
                content.strip(),
                relatedFiles);
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private List<String> reportIndex() {
        return List.of(
                "analysis-report.json",
                "security-report.json",
                "app-documentation.json",
                "AGENTS.md",
                "upgrade-plan.json",
                "change-ledger.json",
                "design-advisory.json",
                "usage-report.json",
                "feature-usage-report.json",
                "apply-report.json");
    }
}
