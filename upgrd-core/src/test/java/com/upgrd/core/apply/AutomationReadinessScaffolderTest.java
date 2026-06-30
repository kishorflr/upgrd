package com.upgrd.core.apply;

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

class AutomationReadinessScaffolderTest {

    @TempDir
    Path tempDir;

    @Test
    void scaffoldsAutomationMetadata() throws Exception {
        Path migrated = tempDir.resolve("migrated");
        Files.createDirectories(migrated.resolve("app-web"));

        UpgradePlan plan = new UpgradePlan(
                "test", false, "java21", "weblogic-14c", "wildfly",
                ProjectProfile.LEGACY_WEB, List.of());

        new AutomationReadinessScaffolder().scaffold(
                migrated, plan, List.of("com.example.UserAction"));

        assertTrue(Files.isRegularFile(migrated.resolve("AGENTS.md")));
        assertTrue(Files.isRegularFile(migrated.resolve("upgrd-analysis.json")));
        assertTrue(Files.isRegularFile(migrated.resolve(".upgrd/manifest.json")));
        assertTrue(Files.isDirectory(migrated.resolve("app-web/src/test/java/com/upgrd/smoke")));
    }
}
