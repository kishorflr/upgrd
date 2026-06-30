package com.upgrd.core.plan;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanApprovalServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultApprovesMandatoryOnly() throws Exception {
        Path source = tempDir.resolve("legacy");
        Path src = source.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "public class App {}");

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_BACKEND);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        UpgradePlan plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", true, security);

        PlanApprovalService service = new PlanApprovalService();
        var approval = service.createDefault(plan, source, true, false, false);

        plan.steps().forEach(step -> {
            if (step.mode() == StepMode.AUTOMATED && step.classification() == ChangeClassification.MANDATORY) {
                assertTrue(approval.isApproved(step.id()), step.id());
            }
            if (step.classification() == ChangeClassification.OPTIONAL) {
                assertFalse(approval.isApproved(step.id()), step.id());
            }
        });

        service.write(approval, tempDir.resolve("out"));
        assertTrue(Files.isRegularFile(tempDir.resolve("out/approved-plan.json")));
    }

    @Test
    void overridesApproveAndReject() throws Exception {
        Path source = tempDir.resolve("legacy");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("src/App.java"), "public class App {}");

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_BACKEND);
        UpgradePlan plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", true,
                new SecurityAnalyzer().analyze(source, discovery));

        PlanApprovalService service = new PlanApprovalService();
        var base = service.createDefault(plan, source);
        String optionalStep = plan.steps().stream()
                .filter(s -> s.classification() == ChangeClassification.OPTIONAL)
                .map(s -> s.id())
                .findFirst()
                .orElse(null);
        if (optionalStep != null) {
            var updated = service.applyOverrides(base, Set.of(optionalStep), Set.of());
            assertTrue(updated.isApproved(optionalStep));
        }
    }
}
