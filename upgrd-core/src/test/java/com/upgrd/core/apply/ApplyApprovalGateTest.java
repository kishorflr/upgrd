package com.upgrd.core.apply;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ApprovedPlan;
import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.StepApproval;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyApprovalGateTest {

    @TempDir
    Path tempDir;

    @Test
    void applySkipsNonApprovedSteps() throws Exception {
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
        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        UpgradePlan fullPlan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);

        UpgradePlan plan = new UpgradePlan(
                fullPlan.upgrdVersion(),
                false,
                fullPlan.targetJava(),
                fullPlan.productionServer(),
                fullPlan.localServer(),
                fullPlan.profile(),
                List.of(
                        new UpgradeStep("convert-maven", "build", "Convert to Maven",
                                "upgrd:ConvertToMaven", "Ant layout", List.of("buildSystem=ANT"),
                                StepMode.AUTOMATED, ChangeClassification.MANDATORY),
                        new UpgradeStep("migrate-log4j1", "logging", "Migrate log4j",
                                "upgrd:Log4j1ToSlf4j", "Log4j EOL", List.of("log4j"),
                                StepMode.AUTOMATED, ChangeClassification.MANDATORY)));

        ApprovedPlan approval = new ApprovedPlan(
                "test",
                Instant.now(),
                plan.upgrdVersion(),
                source.toString(),
                List.of(
                        new StepApproval("convert-maven", true, ChangeClassification.MANDATORY,
                                StepMode.AUTOMATED, "approved"),
                        new StepApproval("migrate-log4j1", false, ChangeClassification.MANDATORY,
                                StepMode.AUTOMATED, "rejected")));

        ApplyEngine engine = new ApplyEngine();
        var report = engine.apply(plan, source, output, approval);

        assertTrue(Files.isRegularFile(output.resolve("migrated/pom.xml")));
        Path javaFile = output.resolve("migrated/app-web/src/main/java/UserAction.java");
        assertTrue(Files.isRegularFile(javaFile));
        String content = Files.readString(javaFile);
        assertTrue(content.contains("org.apache.log4j"));

        long notApproved = report.steps().stream()
                .filter(s -> "NOT_APPROVED".equals(s.status()))
                .count();
        assertEquals(1, notApproved);
    }
}
