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
                false));
        register(new RecipeDefinition(
                "upgrade-java",
                "org.openrewrite.java.migrate.UpgradeToJava21",
                "Upgrade source compatibility to Java 21",
                false));
        register(new RecipeDefinition(
                "portable-jakarta",
                "org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta",
                "Replace javax.* with jakarta.*",
                false));
        register(new RecipeDefinition(
                "weblogic-adapters",
                "upgrd:WebLogic14cDescriptors",
                "Generate deploy/weblogic overlays for production",
                false));
        register(new RecipeDefinition(
                "wildfly-local",
                "upgrd:WildFlyLocalProfile",
                "Generate deploy/wildfly profile for local verification",
                false));
        register(new RecipeDefinition(
                "security-scan",
                "upgrd:SecurityVerify",
                "Run OWASP Dependency-Check and SpotBugs after migration",
                false));
        register(new RecipeDefinition(
                "test-scaffold",
                "upgrd:GenerateSmokeTests",
                "Add JUnit 5 smoke tests for hot paths discovered from logs",
                false));
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
