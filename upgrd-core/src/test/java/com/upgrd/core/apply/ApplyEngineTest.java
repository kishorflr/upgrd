package com.upgrd.core.apply;

import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void applyCopiesSourcesAndMigratesLog4j() throws Exception {
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
        UpgradePlan plan = new UpgradePlan(
                "test",
                false,
                "java21",
                "weblogic-14c",
                "wildfly",
                ProjectProfile.LEGACY_WEB,
                List.of(
                        new UpgradeStep("convert-maven", "build", "Convert to Maven",
                                "upgrd:ConvertToMaven", "Ant layout blocks Maven tooling",
                                List.of("buildSystem=ANT"), StepMode.AUTOMATED, ChangeClassification.MANDATORY),
                        new UpgradeStep("migrate-log4j1", "logging", "Migrate log4j",
                                "upgrd:Log4j1ToSlf4j", "Log4j 1.x is EOL",
                                List.of("log4j"), StepMode.AUTOMATED, ChangeClassification.MANDATORY)));

        ApplyEngine engine = new ApplyEngine();
        var report = engine.apply(plan, source, output);

        Path migratedJava = output.resolve("migrated/app-web/src/main/java/UserAction.java");
        assertTrue(Files.isRegularFile(migratedJava));
        String migrated = Files.readString(migratedJava);
        assertTrue(migrated.contains("LoggerFactory"));
        assertTrue(!migrated.contains("org.apache.log4j"));

        assertTrue(report.steps().stream().anyMatch(s -> "compile-closure".equals(s.stepId())));
        assertTrue(report.steps().stream().filter(s -> "APPLIED".equals(s.status())).count() >= 2);
        assertTrue(Files.isRegularFile(output.resolve("change-ledger.json")));
    }
}
