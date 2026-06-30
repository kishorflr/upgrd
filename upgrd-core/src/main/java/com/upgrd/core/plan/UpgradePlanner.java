package com.upgrd.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UpgradePlanner {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public UpgradePlan plan(
            ProjectDiscovery discovery,
            String targetJava,
            String productionServer,
            boolean dryRun) {
        List<UpgradeStep> steps = new ArrayList<>();

        if (discovery.buildSystem() != BuildSystem.MAVEN) {
            steps.add(new UpgradeStep(
                    "convert-maven",
                    "build",
                    "Convert project layout to Maven multi-module structure",
                    "upgrd:ConvertToMaven"));
        }

        steps.add(new UpgradeStep(
                "upgrade-java",
                "language",
                "Upgrade source compatibility to Java " + targetJava,
                "org.openrewrite.java.migrate.UpgradeToJava" + targetJava.replace("java", "")));

        if (discovery.containsWeblogicApi()) {
            steps.add(new UpgradeStep(
                    "portable-jakarta",
                    "api",
                    "Replace javax.* with jakarta.* and isolate WebLogic-specific APIs",
                    "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta"));
            steps.add(new UpgradeStep(
                    "weblogic-adapters",
                    "server",
                    "Generate deploy/weblogic overlays for production (" + productionServer + ")",
                    "upgrd:WebLogic14cDescriptors"));
        }

        steps.add(new UpgradeStep(
                "wildfly-local",
                "server",
                "Generate deploy/wildfly profile for local verification",
                "upgrd:WildFlyLocalProfile"));

        steps.add(new UpgradeStep(
                "security-scan",
                "security",
                "Run OWASP Dependency-Check and SpotBugs after migration",
                "upgrd:SecurityVerify"));

        steps.add(new UpgradeStep(
                "test-scaffold",
                "testing",
                "Add JUnit 5 smoke tests for hot paths discovered from logs",
                "upgrd:GenerateSmokeTests"));

        return new UpgradePlan(
                AnalyzeEngine.VERSION,
                dryRun,
                targetJava,
                productionServer,
                "wildfly",
                steps);
    }

    public Path writePlan(UpgradePlan plan, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path planFile = outputDir.resolve("upgrade-plan.json");
        mapper.writeValue(planFile.toFile(), plan);
        return planFile;
    }
}
