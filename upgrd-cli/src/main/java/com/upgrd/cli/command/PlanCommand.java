package com.upgrd.cli.command;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.SecurityReport;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityAnalyzer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "plan",
        subcommands = {
                PlanCommand.PlanUpgradeCommand.class,
                PlanPreviewCommand.class,
                PlanApproveCommand.class
        },
        description = "Generate upgrade plans")
public final class PlanCommand implements Runnable {

    @Override
    public void run() {
        picocli.CommandLine.usage(this, System.out);
    }

    @Command(
            name = "upgrade",
            description = "Build a profile-aware OpenRewrite upgrade plan (dry-run by default)")
    public static final class PlanUpgradeCommand implements Callable<Integer> {

        @Option(names = "--source", required = true, description = "Java project source root")
        private Path source;

        @Option(names = "--target", defaultValue = "java21", description = "Target Java version")
        private String target;

        @Option(names = "--server", defaultValue = "weblogic-14c", description = "Production app server")
        private String server;

        @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
        private Path output;

        @Option(names = "--dry-run", defaultValue = "true", description = "Plan only; do not apply changes")
        private boolean dryRun;

        @Option(names = "--profile", description = "Override profile: legacy-web, legacy-backend")
        private String profile;

        @Override
        public Integer call() throws Exception {
            ProjectProfile profileOverride = parseProfile(profile);
            ProjectDiscovery discovery = new ProjectDiscoveryService().discover(source, profileOverride);
            SecurityReport security = new SecurityAnalyzer().analyze(source, discovery);
            ReportWriter reportWriter = new ReportWriter();
            var sync = reportWriter.readSyncReport(output);
            var usage = reportWriter.readUsageReport(output);
            var apiCompatibility = reportWriter.readApiCompatibilityReport(output);
            UpgradePlanner planner = new UpgradePlanner();
            UpgradePlan plan = planner.plan(discovery, target, server, dryRun, security, sync, usage, apiCompatibility);
            Path planFile = planner.writePlan(plan, output);

            reportWriter.writeSecurityReport(security, output);
            reportWriter.writeChangeLedger(reportWriter.previewFromPlan(plan, source), output);

            System.out.printf("UpGrd upgrade plan (%s).%n", dryRun ? "dry-run" : "apply-ready");
            System.out.printf("  Profile: %s | Build: %s%n", plan.profile(), discovery.buildSystem());
            System.out.printf("  Target: %s | Production: %s | Local: wildfly%n", target, server);
            System.out.printf("  Steps: %d (automated + advisory)%n", plan.steps().size());
            plan.steps().forEach(step ->
                    System.out.printf("    - [%s/%s] %s%n",
                            step.category(), step.mode(), step.description()));
            System.out.printf("  Plan: %s%n", planFile.toAbsolutePath());
            System.out.printf("  Security findings: %d (auto-fixable steps added to plan)%n", security.openCount());
            if (sync != null && sync.severity() != null) {
                System.out.printf("  WAR sync: %s — %s%n", sync.severity(), sync.severityReason());
            }
            if (apiCompatibility != null && apiCompatibility.totalHits() > 0) {
                System.out.printf("  API catalog: %s%n", apiCompatibility.summary());
            }
            System.out.printf("  Change ledger preview: %s/change-ledger.json%n", output.toAbsolutePath());
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
}
