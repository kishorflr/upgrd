package com.upgrd.core.integration;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.SyncSeverity;
import com.upgrd.core.model.WarConflictPolicy;
import com.upgrd.core.pipeline.PipelineOrchestrator;
import com.upgrd.core.plan.PlanApprovalService;
import com.upgrd.core.plan.PlanPreviewEngine;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M20 end-to-end: analyze (WAR + logs + source) → plan → preview → approve → apply (WAR merge).
 */
class FullUpgradeWorkflowE2ETest {

    private static Path fixtureRoot;

    @BeforeAll
    static void loadFixture() throws Exception {
        fixtureRoot = Path.of(FullUpgradeWorkflowE2ETest.class.getClassLoader()
                .getResource("fixtures/legacy-e2e-web")
                .toURI());
    }

    @TempDir
    Path tempDir;

    @Test
    void fullReviewFirstWorkflowWithWarLogsAndSource() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createDriftWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");
        List<Path> logs = List.of(
                source.resolve("logs/access.log"),
                source.resolve("logs/server.log"));

        AnalyzeEngine analyzeEngine = new AnalyzeEngine();
        var analysis = analyzeEngine.analyze(
                new AnalysisInput(source, war, logs, output, ProjectProfile.LEGACY_WEB));

        assertTrue(analysis.sync().severity().ordinal() >= SyncSeverity.HIGH.ordinal(),
                "expected WAR/source drift severity HIGH or CRITICAL, got " + analysis.sync().severity());
        assertTrue(analysis.sync().onlyInWar().stream().anyMatch(c -> c.contains("WarOnlyAction")),
                "WAR-only class should be detected");
        assertTrue(analysis.usage().totalHits() > 0, "log usage hits expected");

        ReportWriter reportWriter = new ReportWriter();
        assertTrue(Files.isRegularFile(output.resolve("sync-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("usage-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("feature-usage-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("api-compatibility-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("war-context.json")));

        var security = new SecurityAnalyzer().analyze(source, analysis.discovery());
        var apiCompatibility = reportWriter.readApiCompatibilityReport(output);
        UpgradePlanner planner = new UpgradePlanner();
        UpgradePlan dryPlan = planner.plan(
                analysis.discovery(), "java21", "weblogic-14c", true,
                security, analysis.sync(), analysis.usage(), apiCompatibility);
        planner.writePlan(dryPlan, output);

        var preview = new PlanPreviewEngine().preview(dryPlan, source);
        reportWriter.writeUpgradePreviewReport(preview, output);
        assertTrue(Files.isRegularFile(output.resolve("upgrade-preview-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("change-ledger-preview.json")));

        UpgradePlan plan = planner.plan(
                analysis.discovery(), "java21", "weblogic-14c", false,
                security, analysis.sync(), analysis.usage(), apiCompatibility);
        planner.writePlan(plan, output);
        assertTrue(plan.steps().stream().anyMatch(s -> "war-authoritative-merge".equals(s.id())));

        PlanApprovalService approvalService = new PlanApprovalService();
        var approval = approvalService.createDefault(plan, source, true, true, false);
        approvalService.write(approval, output);
        assertTrue(Files.isRegularFile(output.resolve(PlanApprovalService.APPROVED_PLAN_FILE)));

        var warOptions = reportWriter.resolveWarApplyOptions(output, war, WarConflictPolicy.MARK_CONFLICT);
        ApplyEngine applyEngine = new ApplyEngine();
        var applyReport = applyEngine.apply(plan, source, output, approval, warOptions);

        long applied = applyReport.steps().stream().filter(s -> "APPLIED".equals(s.status())).count();
        assertTrue(applied >= 5, "expected multiple applied steps, got " + applied);
        assertTrue(applyReport.steps().stream()
                .anyMatch(s -> "war-authoritative-merge".equals(s.stepId()) && "APPLIED".equals(s.status())));

        assertTrue(Files.isRegularFile(output.resolve("war-merge-report.json")));
        Path warOnlyClass = output.resolve(
                "migrated/app-web/src/main/webapp/WEB-INF/classes/com/example/WarOnlyAction.class");
        assertTrue(Files.isRegularFile(warOnlyClass), "WAR-only class should be merged into migrated layout");
        assertTrue(Files.isRegularFile(
                output.resolve("migrated/app-web/.upgrd/war-stubs/com/example/WarOnlyAction.java")));

        Path migratedAction = output.resolve("migrated/app-web/src/main/java/com/example/UserAction.java");
        assertTrue(Files.isRegularFile(migratedAction));
        String migrated = Files.readString(migratedAction);
        assertTrue(migrated.contains("@Controller"), "Struts action should migrate to Spring controller");
    }

    @Test
    void pipelineStopsAtPreviewUntilConfirm() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createDriftWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");
        List<Path> logs = List.of(
                source.resolve("logs/access.log"),
                source.resolve("logs/server.log"));

        PipelineOrchestrator orchestrator = new PipelineOrchestrator();
        var stopped = orchestrator.run(new PipelineOrchestrator.PipelineRequest(
                source, war, logs, null, output, ProjectProfile.LEGACY_WEB,
                "java21", "weblogic-14c",
                false, false, false, false, false, false, false, true,
                false, false, null, false));

        assertTrue(stopped.success());
        assertEquals(List.of("analyze", "plan", "preview"), stopped.completedPhases());
        assertNull(stopped.applyReport());
        assertTrue(Files.isRegularFile(output.resolve("upgrade-preview-report.json")));
        assertTrue(!Files.isRegularFile(output.resolve("apply-report.json")));
    }

    @Test
    void pipelineApplyWithAutoApproveMandatory() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createDriftWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");
        List<Path> logs = List.of(
                source.resolve("logs/access.log"),
                source.resolve("logs/server.log"));

        PipelineOrchestrator orchestrator = new PipelineOrchestrator();
        var result = orchestrator.run(new PipelineOrchestrator.PipelineRequest(
                source, war, logs, null, output, ProjectProfile.LEGACY_WEB,
                "java21", "weblogic-14c",
                true, true, false, false, false, false, false, true,
                false, false, null, false));

        assertTrue(result.success());
        assertTrue(result.completedPhases().contains("apply"));
        assertNotNull(result.applyReport());
        assertTrue(Files.isRegularFile(output.resolve("war-merge-report.json")));
        assertTrue(Files.isRegularFile(output.resolve(PlanApprovalService.APPROVED_PLAN_FILE)));
    }

    private Path copyFixture(Path target) throws IOException {
        copyRecursive(fixtureRoot, target);
        return target;
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private Path createDriftWar(Path warPath) throws IOException {
        Files.createDirectories(warPath.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(warPath))) {
            zos.putNextEntry(new ZipEntry("WEB-INF/"));
            zos.closeEntry();
            writeEntry(zos, "WEB-INF/web.xml", Files.readString(fixtureRoot.resolve("WEB-INF/web.xml")));
            writeEntry(zos, "WEB-INF/classes/com/example/UserAction.class", new byte[] {0});
            writeEntry(zos, "WEB-INF/classes/com/example/WarOnlyAction.class", new byte[] {0});
            writeEntry(zos, "WEB-INF/lib/legacy-extra.jar", new byte[] {0});
        }
        return warPath;
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private void writeEntry(ZipOutputStream zos, String name, String text) throws IOException {
        writeEntry(zos, name, text.getBytes());
    }
}
