package com.upgrd.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps upgrade-plan step IDs to OpenRewrite recipe coordinates.
 * M2: catalog only; execution wired in {@link com.upgrd.core.apply.ApplyEngine}.
 */
public final class RecipeCatalog {

    private final Map<String, RecipeDefinition> byStepId = new LinkedHashMap<>();

    public RecipeCatalog() {
        register(new RecipeDefinition(
                "convert-maven",
                "upgrd:ConvertToMaven",
                "Convert project layout to Maven multi-module structure",
                true));
        register(new RecipeDefinition(
                "migrate-log4j1",
                "upgrd:Log4j1ToSlf4j",
                "Migrate log4j 1.x to SLF4J",
                true));
        register(new RecipeDefinition(
                "struts-to-spring-mvc",
                "upgrd:StrutsActionToSpringController",
                "Migrate Struts actions to Spring MVC controllers",
                true));
        register(new RecipeDefinition(
                "struts-config-to-spring",
                "upgrd:StrutsConfigToSpring",
                "Migrate Struts config action mappings to Spring MVC hints",
                true));
        register(new RecipeDefinition(
                "struts-view-to-spring",
                "upgrd:StrutsViewToSpringHints",
                "Generate Struts JSP and validation.xml → Spring MVC hints",
                true));
        register(new RecipeDefinition(
                "struts-jsp-to-thymeleaf",
                "upgrd:StrutsJspToThymeleaf",
                "Scaffold Thymeleaf templates from Struts JSP views",
                true));
        register(new RecipeDefinition(
                "thymeleaf-wiring",
                "upgrd:ThymeleafWiring",
                "Wire Thymeleaf view resolver and Maven dependencies",
                true));
        register(new RecipeDefinition(
                "spring-4-to-6",
                "upgrd:Spring4To6",
                "Upgrade Spring MVC 4.x to Spring 6",
                true));
        register(new RecipeDefinition(
                "upgrade-java",
                "upgrd:UpgradeToJava21",
                "Upgrade source compatibility to Java 21",
                true));
        register(new RecipeDefinition(
                "replace-raw-collections",
                "upgrd:ReplaceRawCollections",
                "Replace raw and legacy collection types",
                true));
        register(new RecipeDefinition(
                "introduce-layering",
                "upgrd:ExtractServiceLayer",
                "Propose service/repository layer extraction (advisory)",
                false));
        register(new RecipeDefinition(
                "add-interfaces",
                "upgrd:SuggestAbstractions",
                "Suggest abstractions for tightly coupled classes (advisory)",
                false));
        register(new RecipeDefinition(
                "remediate-weak-crypto",
                "upgrd:RemediateWeakHash",
                "Replace weak hash algorithms with SHA-256",
                true));
        register(new RecipeDefinition(
                "remediate-secrets",
                "upgrd:ExternalizeSecrets",
                "Externalize hardcoded credentials to environment variables",
                true));
        register(new RecipeDefinition(
                "portable-jakarta",
                "upgrd:JavaxToJakarta",
                "Replace javax.* with jakarta.*",
                true));
        register(new RecipeDefinition(
                "weblogic-adapters",
                "upgrd:WebLogic14cDescriptors",
                "Generate deploy/weblogic overlays for production",
                true));
        register(new RecipeDefinition(
                "wildfly-local",
                "upgrd:WildFlyLocalProfile",
                "Generate deploy/wildfly profile for local verification",
                true));
        register(new RecipeDefinition(
                "security-verify",
                "upgrd:SecurityVerify",
                "Run OWASP Dependency-Check and SpotBugs after migration",
                true));
        register(new RecipeDefinition(
                "test-scaffold",
                "upgrd:GenerateSmokeTests",
                "Add JUnit 5 smoke tests inside migrated app",
                true));
        register(new RecipeDefinition(
                "remediate-sql-concatenation",
                "upgrd:RemediateSqlConcatenation",
                "Document SQL concatenation hotspots for PreparedStatement refactor",
                true));
        register(new RecipeDefinition(
                "remediate-deserialization",
                "upgrd:RemediateDeserialization",
                "Document unsafe deserialization hotspots for manual refactor",
                true));
        register(new RecipeDefinition(
                "openrewrite-scaffold",
                "upgrd:OpenRewriteScaffold",
                "Scaffold OpenRewrite YAML for optional AST migrations",
                true));
        register(new RecipeDefinition(
                "openrewrite-dry-run",
                "upgrd:OpenRewriteDryRun",
                "Run OpenRewrite dry-run as migration gate",
                true));
        register(new RecipeDefinition(
                "openrewrite-apply",
                "upgrd:OpenRewriteApply",
                "Apply OpenRewrite AST migrations (advisory)",
                false));
        register(new RecipeDefinition(
                "automation-ready",
                "upgrd:AutomationReady",
                "Embed AI/automation-friendly metadata in migrated application",
                true));
    }

    public Optional<RecipeDefinition> findByStepId(String stepId) {
        return Optional.ofNullable(byStepId.get(stepId));
    }

    public Map<String, RecipeDefinition> all() {
        return Map.copyOf(byStepId);
    }

    private void register(RecipeDefinition definition) {
        byStepId.put(definition.stepId(), definition);
    }
}
