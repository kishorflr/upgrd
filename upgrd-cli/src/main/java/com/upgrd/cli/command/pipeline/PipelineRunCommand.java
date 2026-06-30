package com.upgrd.cli.command.pipeline;

import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.pipeline.PipelineOrchestrator;
import com.upgrd.core.pipeline.PipelineOrchestrator.PipelineRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "run",
        description = "Analyze → plan → apply → verify in one edge-local pipeline")
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

    @Override
    public Integer call() throws Exception {
        var result = new PipelineOrchestrator().run(new PipelineRequest(
                source,
                war,
                logs,
                output,
                parseProfile(profile),
                target,
                server,
                !skipVerify,
                securityScan,
                wildflySmoke,
                wildflyDeploy,
                wildflyHttp));

        System.out.println("UpGrd pipeline complete.");
        System.out.printf("  Phases: %s%n", String.join(" → ", result.completedPhases()));
        System.out.printf("  Plan: %s%n", result.planFile().toAbsolutePath());
        System.out.printf("  Migrated: %s%n", result.applyReport().migratedRoot());
        if (result.verifyResult() != null) {
            System.out.printf("  Verify: %s (exit %d)%n",
                    result.verifyResult().passed() ? "PASSED" : "FAILED",
                    result.verifyResult().exitCode());
            System.out.printf("  Verify report: %s%n", result.verifyResult().reportPath().toAbsolutePath());
        }
        return result.success() ? 0 : 1;
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
