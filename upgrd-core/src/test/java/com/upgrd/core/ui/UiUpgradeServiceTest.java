package com.upgrd.core.ui;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.AnalyzeWorkspace;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.plan.PlanApprovalService;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiUpgradeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void applyApprovedFinalizesDryRunPlanAndApplies() throws Exception {
        Path source = tempDir.resolve("legacy");
        Path src = source.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("UserAction.java"), """
                import org.apache.log4j.Logger;
                public class UserAction {
                    private static Logger log = Logger.getLogger(UserAction.class);
                }
                """);

        Path output = tempDir.resolve("out");
        Files.createDirectories(output);

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", true, security);
        new UpgradePlanner().writePlan(plan, output);

        var approval = new PlanApprovalService().createDefault(plan, source, true, true, false);
        new PlanApprovalService().write(approval, output);

        UiUpgradeService service = new UiUpgradeService();
        var result = service.applyApproved(output, "war-wins");

        assertTrue(result.success());
        assertTrue(result.appliedCount() > 0);
        assertTrue(Files.isRegularFile(output.resolve("migrated/pom.xml")));
        assertTrue(Files.isRegularFile(output.resolve("apply-report.json")));

        var reloaded = new ApplyEngine().loadPlan(output.resolve("upgrade-plan.json"));
        assertFalse(reloaded.dryRun(), "UI apply should finalize dry-run plan");
    }

    @Test
    void applyUsesWorkspaceSourceWhenApprovalSourceMissing() throws Exception {
        Path source = tempDir.resolve("legacy");
        Path src = source.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "public class App {}");

        Path output = tempDir.resolve("out");
        Files.createDirectories(output);

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_BACKEND);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);
        new UpgradePlanner().writePlan(plan, output);

        var approvalService = new PlanApprovalService();
        var approval = approvalService.createDefault(plan, source, true, false, false);
        approvalService.write(approval, output);

        new WorkspaceStore().save(output, new AnalyzeWorkspace(
                source.toAbsolutePath().normalize().toString(),
                null,
                output.toAbsolutePath().normalize().toString(),
                null));

        UiUpgradeService service = new UiUpgradeService();
        var result = service.applyApproved(output, "war-wins");

        assertTrue(result.success());
        assertTrue(Files.isRegularFile(output.resolve("migrated/pom.xml")));
    }
}
