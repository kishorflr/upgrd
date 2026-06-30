package com.upgrd.core.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AutomationManifest;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.UpgradePlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Makes migrated applications easy to analyze by automation tools and AI agents.
 */
public final class AutomationReadinessScaffolder {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public List<String> scaffold(Path migratedRoot, UpgradePlan plan, List<String> entryPoints) throws IOException {
        Path appWeb = migratedRoot.resolve("app-web");
        Path testJava = appWeb.resolve("src/test/java/com/upgrd/smoke");
        Path upgrdMeta = migratedRoot.resolve(".upgrd");

        Files.createDirectories(testJava);
        Files.createDirectories(upgrdMeta);
        Files.createDirectories(appWeb.resolve("src/test/resources"));

        AutomationManifest manifest = new AutomationManifest(
                AnalyzeEngine.VERSION,
                Instant.now(),
                plan.profile(),
                "mvn -f migrated/pom.xml test",
                Map.of(
                        "mainSources", "app-web/src/main/java",
                        "testSources", "app-web/src/test/java",
                        "webapp", "app-web/src/main/webapp",
                        "wildflyDeploy", "deploy/wildfly",
                        "weblogicDeploy", "deploy/weblogic"),
                entryPoints,
                List.of(
                        "Maven standard layout — use pom.xml for dependency graph",
                        "Entry points list log-discovered hot paths; start tests there",
                        "Smoke tests live under com.upgrd.smoke",
                        "See AGENTS.md in migrated/ root for onboarding",
                        "Human audit reports remain in upgrd-out/ beside migrated/"));

        Path manifestFile = migratedRoot.resolve("upgrd-analysis.json");
        mapper.writeValue(manifestFile.toFile(), manifest);
        Files.writeString(upgrdMeta.resolve("manifest.json"), mapper.writeValueAsString(manifest));
        Files.writeString(migratedRoot.resolve("AGENTS.md"), agentsMarkdown(plan, manifest));

        return List.of(
                "upgrd-analysis.json",
                ".upgrd/manifest.json",
                "AGENTS.md",
                "app-web/src/test/java/com/upgrd/smoke/");
    }

    private String agentsMarkdown(UpgradePlan plan, AutomationManifest manifest) {
        StringBuilder md = new StringBuilder();
        md.append("# Migrated Application — Agent Guide\n\n");
        md.append("This application was upgraded by **UpGrd**. Use this file to onboard automation tools and AI agents.\n\n");
        md.append("## Quick facts\n\n");
        md.append("- **Profile:** ").append(plan.profile()).append("\n");
        md.append("- **Java target:** ").append(plan.targetJava()).append("\n");
        md.append("- **Run tests:** `").append(manifest.testCommand()).append("`\n\n");
        md.append("## Layout\n\n");
        manifest.layout().forEach((k, v) -> md.append("- **").append(k).append(":** `").append(v).append("`\n"));
        md.append("\n## Entry points (hot paths)\n\n");
        if (manifest.entryPoints().isEmpty()) {
            md.append("_No log-derived entry points — scan `app-web/src/main/java`._\n\n");
        } else {
            manifest.entryPoints().forEach(ep -> md.append("- `").append(ep).append("`\n"));
            md.append("\n");
        }
        md.append("## Analysis hints\n\n");
        manifest.analysisHints().forEach(h -> md.append("- ").append(h).append("\n"));
        md.append("\n## Audit trail\n\n");
        md.append("Upgrade reasoning, security fixes, and diffs are in the sibling `upgrd-out/` directory:\n");
        md.append("`change-ledger.json`, `security-report.json`, `app-documentation.json`\n");
        return md.toString();
    }
}
