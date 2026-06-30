package com.upgrd.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.LoggingFramework;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.ServletApi;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.TechnologyFingerprint;
import com.upgrd.core.model.SecurityFinding;
import com.upgrd.core.model.SecurityReport;
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
            boolean dryRun,
            SecurityReport security) {
        List<UpgradeStep> steps = new ArrayList<>();
        ProjectProfile profile = discovery.profile();
        TechnologyFingerprint fp = discovery.fingerprint();

        if (discovery.buildSystem() != BuildSystem.MAVEN) {
            String recipe = profile == ProjectProfile.LEGACY_WEB
                    ? "upgrd:ConvertAntWarToMaven"
                    : "upgrd:ConvertFlatToMaven";
            steps.add(step(
                    "convert-maven",
                    "build",
                    profile == ProjectProfile.LEGACY_WEB
                            ? "Convert Ant WAR layout to Maven multi-module structure"
                            : "Convert flat/Ant layout to Maven structure",
                    recipe,
                    "Non-Maven builds block dependency analysis, security scanning, and reproducible Java "
                            + targetJava + " migration",
                    List.of("buildSystem=" + discovery.buildSystem()),
                    StepMode.AUTOMATED));
        }

        steps.add(step(
                "upgrade-java",
                "language",
                "Upgrade source compatibility to Java " + targetJava,
                "upgrd:UpgradeToJava21",
                "Target runtime requires Java " + targetJava + "; language features and deprecated APIs must be updated",
                List.of("javaVersionHint=" + discovery.javaVersionHint()),
                StepMode.AUTOMATED));

        if (profile == ProjectProfile.LEGACY_WEB) {
            addLegacyWebSteps(steps, fp, targetJava);
        }

        if (profile == ProjectProfile.LEGACY_BACKEND) {
            addLegacyBackendSteps(steps, fp);
        }

        addSecurityRemediationSteps(steps, security);

        if (discovery.containsWeblogicApi() || fp.servletApi() == ServletApi.JAVAX) {
            steps.add(step(
                    "portable-jakarta",
                    "api",
                    "Replace javax.* with jakarta.* and isolate WebLogic-specific APIs",
                    "upgrd:JavaxToJakarta",
                    "Jakarta EE namespace is required for modern servlet containers and Spring 6+",
                    fp.evidence().stream().filter(e -> e.contains("javax")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "weblogic-adapters",
                    "server",
                    "Generate deploy/weblogic overlays for production (" + productionServer + ")",
                    "upgrd:WebLogic14cDescriptors",
                    "Production deployment targets " + productionServer + "; bindings stay out of portable code",
                    List.of("containsWeblogicApi=" + discovery.containsWeblogicApi()),
                    StepMode.AUTOMATED));
        }

        steps.add(step(
                "wildfly-local",
                "server",
                "Generate deploy/wildfly profile for local verification",
                "upgrd:WildFlyLocalProfile",
                "Local WildFly profile enables edge-only verification before production WebLogic deploy",
                List.of("localServer=wildfly"),
                StepMode.AUTOMATED));

        steps.add(step(
                "security-verify",
                "security",
                "Run OWASP Dependency-Check and SpotBugs after migration",
                "upgrd:SecurityVerify",
                "Post-upgrade verification confirms no new CVEs were introduced",
                fp.evidence().stream().filter(e -> e.startsWith("classpath:")).limit(5).toList(),
                StepMode.AUTOMATED));

        steps.add(step(
                "test-scaffold",
                "testing",
                "Add JUnit 5 smoke tests inside migrated app for hot paths",
                "upgrd:GenerateSmokeTests",
                "Automated tests in app-web/src/test/java give agents and CI a baseline; hot paths from logs are prioritized",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        steps.add(step(
                "openrewrite-scaffold",
                "tooling",
                "Scaffold OpenRewrite config for deeper AST migrations",
                "upgrd:OpenRewriteScaffold",
                "File-level recipes cover safe transforms; OpenRewrite YAML enables optional AST passes when teams are ready",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        steps.add(step(
                "automation-ready",
                "tooling",
                "Embed AI/automation-friendly metadata in migrated application",
                "upgrd:AutomationReady",
                "Standard Maven test layout, upgrd-analysis.json, and AGENTS.md in migrated/ help future tools analyze the upgraded codebase",
                List.of("profile=" + profile),
                StepMode.AUTOMATED));

        return new UpgradePlan(
                AnalyzeEngine.VERSION,
                dryRun,
                targetJava,
                productionServer,
                "wildfly",
                profile,
                steps);
    }

    private void addLegacyWebSteps(List<UpgradeStep> steps, TechnologyFingerprint fp, String targetJava) {
        if (fp.logging() == LoggingFramework.LOG4J_1 || fp.logging() == LoggingFramework.MIXED) {
            steps.add(step(
                    "migrate-log4j1",
                    "logging",
                    "Migrate log4j 1.x to SLF4J",
                    "upgrd:Log4j1ToSlf4j",
                    "Log4j 1.x is EOL with known CVEs; SLF4J is required for Spring Boot 3 and modern stacks",
                    fp.evidence().stream().filter(e -> e.contains("log4j")).limit(5).toList(),
                    StepMode.AUTOMATED));
        }

        if (fp.frameworks().stream().anyMatch(f -> f.startsWith("STRUTS"))) {
            steps.add(step(
                    "struts-to-spring-mvc",
                    "framework",
                    "Migrate Struts actions to Spring MVC controllers",
                    "upgrd:StrutsActionToSpringController",
                    "Struts is unmaintained; unify on Spring MVC for a single web framework on Java " + targetJava,
                    fp.evidence().stream().filter(e -> e.contains("Struts")).limit(5).toList(),
                    StepMode.AUTOMATED));
            steps.add(step(
                    "struts-config-to-spring",
                    "framework",
                    "Convert struts-config.xml action mappings to Spring MVC hints",
                    "upgrd:StrutsConfigToSpring",
                    "Struts URL mappings must be recreated in Spring MVC; UpGrd generates a starter config from struts-config.xml",
                    fp.evidence().stream().filter(e -> e.contains("struts-config")).limit(5).toList(),
                    StepMode.AUTOMATED));
        }

        if (fp.frameworks().stream().anyMatch(f -> f.startsWith("SPRING_MVC"))) {
            steps.add(step(
                    "spring-4-to-6",
                    "framework",
                    "Upgrade Spring MVC 4.x to Spring 6 / Boot 3 baseline",
                    "upgrd:Spring4To6",
                    "Spring 4.x is incompatible with Jakarta EE and Java 21 without upgrading to Spring 6",
                    fp.evidence().stream().filter(e -> e.contains("spring")).limit(5).toList(),
                    StepMode.AUTOMATED));
        }
    }

    private void addSecurityRemediationSteps(List<UpgradeStep> steps, SecurityReport security) {
        if (security == null || security.findings().isEmpty()) {
            return;
        }
        for (SecurityFinding finding : security.findings()) {
            if (!finding.autoFixable() || finding.recipeId() == null) {
                continue;
            }
            String stepId = stepIdForRecipe(finding.recipeId());
            if (steps.stream().anyMatch(s -> s.id().equals(stepId))) {
                continue;
            }
            steps.add(step(
                    stepId,
                    "security",
                    "Remediate: " + finding.description(),
                    finding.recipeId(),
                    finding.remediation() + (finding.cveId() != null ? " (" + finding.cveId() + ")" : ""),
                    List.of(finding.file()),
                    StepMode.AUTOMATED));
        }
    }

    private String stepIdForRecipe(String recipeId) {
        return switch (recipeId) {
            case "upgrd:Log4j1ToSlf4j" -> "migrate-log4j1";
            case "upgrd:RemediateWeakHash" -> "remediate-weak-crypto";
            case "upgrd:ExternalizeSecrets" -> "remediate-secrets";
            case "upgrd:RemediateSqlConcatenation" -> "remediate-sql-concatenation";
            case "upgrd:RemediateDeserialization" -> "remediate-deserialization";
            default -> "remediate-" + recipeId.replace("upgrd:", "").toLowerCase();
        };
    }

    private void addLegacyBackendSteps(List<UpgradeStep> steps, TechnologyFingerprint fp) {
        if (fp.riskSignals().contains("raw-collections") || fp.riskSignals().contains("legacy-collections")) {
            steps.add(step(
                    "replace-raw-collections",
                    "typing",
                    "Replace raw and legacy collection types",
                    "upgrd:ReplaceRawCollections",
                    "Parameterized collections improve type safety and enable safer automated refactors",
                    fp.evidence().stream()
                            .filter(e -> e.contains("raw") || e.contains("Vector"))
                            .limit(5)
                            .toList(),
                    StepMode.AUTOMATED));
        }

        steps.add(step(
                "introduce-layering",
                "architecture",
                "Propose service/repository layer extraction",
                "upgrd:ExtractServiceLayer",
                "Backend code without clear layering is harder to test; UpGrd surfaces candidates for manual review",
                fp.riskSignals(),
                StepMode.ADVISORY));

        steps.add(step(
                "add-interfaces",
                "architecture",
                "Suggest abstractions for tightly coupled classes",
                "upgrd:SuggestAbstractions",
                "Introducing interfaces improves testability; advisory-only to avoid silent structural changes",
                fp.riskSignals(),
                StepMode.ADVISORY));
    }

    private UpgradeStep step(
            String id,
            String category,
            String description,
            String recipe,
            String reason,
            List<String> evidence,
            StepMode mode) {
        return new UpgradeStep(id, category, description, recipe, reason, evidence, mode);
    }

    public Path writePlan(UpgradePlan plan, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path planFile = outputDir.resolve("upgrade-plan.json");
        mapper.writeValue(planFile.toFile(), plan);
        return planFile;
    }
}
