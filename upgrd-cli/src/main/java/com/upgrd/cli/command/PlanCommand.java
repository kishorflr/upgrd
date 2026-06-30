package com.upgrd.cli.command;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.plan.UpgradePlanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "plan",
        subcommands = PlanCommand.PlanUpgradeCommand.class,
        description = "Generate upgrade plans")
public final class PlanCommand implements Runnable {

    @Override
    public void run() {
        picocli.CommandLine.usage(this, System.out);
    }

    @Command(
            name = "upgrade",
            description = "Build an OpenRewrite-oriented upgrade plan (dry-run by default)")
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

        @Override
        public Integer call() throws Exception {
            ProjectDiscovery discovery = new ProjectDiscoveryService().discover(source);
            UpgradePlanner planner = new UpgradePlanner();
            UpgradePlan plan = planner.plan(discovery, target, server, dryRun);
            Path planFile = planner.writePlan(plan, output);

            System.out.printf("UpGrd upgrade plan (%s).%n", dryRun ? "dry-run" : "apply-ready");
            System.out.printf("  Build system: %s%n", discovery.buildSystem());
            System.out.printf("  Target: %s | Production: %s | Local: wildfly%n", target, server);
            System.out.printf("  Steps: %d%n", plan.steps().size());
            plan.steps().forEach(step ->
                    System.out.printf("    - [%s] %s (%s)%n", step.category(), step.description(), step.recipe()));
            System.out.printf("  Plan: %s%n", planFile.toAbsolutePath());
            return 0;
        }
    }
}
