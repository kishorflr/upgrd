package com.upgrd.cli.command.pipeline;

import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.openrewrite.OpenRewriteRunner;
import com.upgrd.core.pipeline.PipelineOrchestrator;
import com.upgrd.core.pipeline.PipelineOrchestrator.PipelineRequest;
import com.upgrd.core.ui.ReportServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "run",
        description = "Analyze → plan → preview → apply (with --confirm) → verify")
public final class PipelineRunCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "Java project source root")
    private Path source;

    @Option(names = "--war", description = "Deployed WAR file (optional for backend profile)")
    private Path war;

    @Option(names = "--logs", split = ",", description = "Comma-separated log file paths")
    private List<Path> logs = new ArrayList<>();

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Option(names = "--profile", description = "Override profile: legacy-web, legacy-backend")
    private String profile;

    @Option(names = "--target", defaultValue = "java21", description = "Target Java version")
    private String target;

    @Option(names = "--server", defaultValue = "weblogic-14c", description = "Production app server")
    private String server;

    @Option(names = "--confirm", defaultValue = "false",
            description = "Apply upgrade after preview (default stops at preview for review)")
    private boolean confirm;

    @Option(names = "--skip-verify", defaultValue = "false", description = "Stop after apply (skip mvn verify)")
    private boolean skipVerify;

    @Option(names = "--security-scan", defaultValue = "false", description = "Run verify with -Psecurity-verify")
    private boolean securityScan;

    @Option(names = "--wildfly-smoke", defaultValue = "false", description = "After verify, WildFly scaffold check")
    private boolean wildflySmoke;

    @Option(names = "--wildfly-deploy", defaultValue = "false", description = "After verify, build and stage WAR")
    private boolean wildflyDeploy;

    @Option(names = "--wildfly-http", defaultValue = "false", description = "After verify, HTTP probe WildFly")
    private boolean wildflyHttp;

    @Option(names = "--no-wildfly-http", defaultValue = "false",
            description = "Disable default WildFly HTTP probe for legacy-web profile")
    private boolean noWildflyHttp;

    @Option(names = "--serve-ui", defaultValue = "false",
            description = "After pipeline completes, start the audit dashboard (blocks until Ctrl+C)")
    private boolean serveUi;

    @Option(names = "--port", defaultValue = "8765", description = "Audit dashboard port when --serve-ui is set")
    private int port;

    @Option(names = "--rewrite", defaultValue = "false",
            description = "After apply, run OpenRewrite AST migrations")
    private boolean rewrite;

    @Option(names = "--rewrite-dry-run", defaultValue = "false",
            description = "With --rewrite, preview OpenRewrite changes without modifying sources")
    private boolean rewriteDryRun;

    @Option(names = "--rewrite-recipe", defaultValue = OpenRewriteRunner.DEFAULT_RECIPE,
            description = "OpenRewrite recipe when --rewrite is set")
    private String rewriteRecipe;

    @Option(names = "--rewrite-after-verify", defaultValue = "false",
            description = "Run OpenRewrite after verify instead of after apply")
    private boolean rewriteAfterVerify;

    @Option(names = "--rewrite-sql-scan", defaultValue = "false",
            description = "Preset: SQL concatenation OpenRewrite dry-run after verify")
    private boolean rewriteSqlScan;

    @Override
    public Integer call() throws Exception {
        boolean effectiveRewrite = rewrite || rewriteSqlScan;
        boolean effectiveAfterVerify = rewriteAfterVerify || rewriteSqlScan;
        boolean effectiveDryRun = rewriteDryRun || rewriteSqlScan;
        String effectiveRecipe = rewriteSqlScan ? OpenRewriteRunner.SQL_SCAN_RECIPE : rewriteRecipe;

        var result = new PipelineOrchestrator().run(new PipelineRequest(
                source,
                war,
                logs,
                output,
                parseProfile(profile),
                target,
                server,
                confirm,
                !skipVerify,
                securityScan,
                wildflySmoke,
                wildflyDeploy,
                wildflyHttp,
                !noWildflyHttp,
                effectiveRewrite,
                effectiveDryRun,
                effectiveRecipe,
                effectiveAfterVerify));

        System.out.println("UpGrd pipeline complete.");
        System.out.printf("  Phases: %s%n", String.join(" → ", result.completedPhases()));
        System.out.printf("  Plan: %s%n", result.planFile().toAbsolutePath());
        if (result.previewReportFile() != null) {
            System.out.printf("  Preview: %s%n", result.previewReportFile().toAbsolutePath());
        }
        if (result.applyReport() != null) {
            System.out.printf("  Migrated: %s%n", result.applyReport().migratedRoot());
        } else if (!result.completedPhases().contains("apply")) {
            System.out.println("  Apply skipped — review preview in UI, then re-run with --confirm");
        }
        if (result.rewriteResult() != null) {
            System.out.printf("  Rewrite: %s%n", result.rewriteResult().message());
        }
        if (result.verifyResult() != null) {
            System.out.printf("  Verify: %s (exit %d)%n",
                    result.verifyResult().passed() ? "PASSED" : "FAILED",
                    result.verifyResult().exitCode());
            System.out.printf("  Verify report: %s%n", result.verifyResult().reportPath().toAbsolutePath());
        }

        if (!result.success()) {
            return 1;
        }

        if (serveUi) {
            try (ReportServer server = new ReportServer(output, port)) {
                server.start();
                System.out.printf("UpGrd audit dashboard running at %s%n", server.baseUrl());
                System.out.println("  Press Ctrl+C to stop.");
                Thread.currentThread().join();
            }
        }
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
