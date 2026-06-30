package com.upgrd.cli.command;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.SecurityReport;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.plan.PlanPreviewEngine;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityAnalyzer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "preview",
        description = "Preview file-level upgrade diffs (before/after) without applying changes")
public final class PlanPreviewCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "Java project source root")
    private Path source;

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Option(names = "--plan", description = "Existing upgrade-plan.json (optional; generates plan if missing)")
    private Path planFile;

    @Option(names = "--profile", description = "Override profile: legacy-web, legacy-backend")
    private String profile;

    @Option(names = "--target", defaultValue = "java21", description = "Target Java version")
    private String target;

    @Option(names = "--server", defaultValue = "weblogic-14c", description = "Production app server")
    private String server;

    @Override
    public Integer call() throws Exception {
        UpgradePlan plan;
        if (planFile != null && Files.isRegularFile(planFile)) {
            plan = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(planFile.toFile(), UpgradePlan.class);
        } else {
            ProjectProfile profileOverride = parseProfile(profile);
            ProjectDiscovery discovery = new ProjectDiscoveryService().discover(source, profileOverride);
            SecurityReport security = new SecurityAnalyzer().analyze(source, discovery);
            plan = new UpgradePlanner().plan(discovery, target, server, true, security);
            new UpgradePlanner().writePlan(plan, output);
        }

        var preview = new PlanPreviewEngine().preview(plan, source);
        Path reportFile = new ReportWriter().writeUpgradePreviewReport(preview, output);

        System.out.printf("UpGrd upgrade preview.%n");
        System.out.printf("  Steps: %d automated, %d advisory%n", preview.automatedSteps(), preview.advisorySteps());
        System.out.printf("  Previewed file changes: %d%n", preview.previewedFileChanges());
        preview.steps().forEach(s ->
                System.out.printf("    - [%s/%s] %s — %d file(s)%n",
                        s.classification(), s.mode(), s.description(), s.previewedFileChanges()));
        System.out.printf("  Preview report: %s%n", reportFile.toAbsolutePath());
        System.out.printf("  Change ledger preview: %s/change-ledger-preview.json%n", output.toAbsolutePath());
        System.out.println("  Review in UI: upgrd run --serve-ui --output " + output);
        System.out.println("  Apply after review: upgrd plan upgrade --dry-run=false ... && upgrd apply --plan ...");
        return 0;
    }

    private ProjectProfile parseProfile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.toLowerCase().replace('_', '-')) {
            case "legacy-web" -> ProjectProfile.LEGACY_WEB;
            case "legacy-backend" -> ProjectProfile.LEGACY_BACKEND;
            default -> throw new IllegalArgumentException("Unknown profile: " + value);
        };
    }
}
