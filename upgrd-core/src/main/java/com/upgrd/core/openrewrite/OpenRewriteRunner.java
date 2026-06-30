package com.upgrd.core.openrewrite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs OpenRewrite via the Maven plugin against the migrated project.
 */
public final class OpenRewriteRunner {

    private static final String ACTIVE_RECIPE = "com.upgrd.migrated.UpgradeBaseline";

    private final OpenRewriteMavenIntegrator integrator = new OpenRewriteMavenIntegrator();

    public RewriteResult run(Path outputDir, boolean dryRun) throws IOException, InterruptedException {
        return run(outputDir, dryRun, false);
    }

    public RewriteResult run(Path outputDir, boolean dryRun, boolean requireDryRunGate)
            throws IOException, InterruptedException {
        Path migrated = outputDir.resolve("migrated").toAbsolutePath().normalize();
        Path pom = migrated.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            throw new IOException("Migrated POM not found: " + pom);
        }

        if (!dryRun && requireDryRunGate) {
            Path gate = migrated.resolve(".upgrd/rewrite/dry-run-passed");
            if (!Files.isRegularFile(gate)) {
                return new RewriteResult(false, 1,
                        "OpenRewrite dry-run gate not passed — run `upgrd apply` or `upgrd rewrite run --dry-run` first",
                        "");
            }
        }

        integrator.ensurePluginConfigured(migrated);

        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("-f");
        command.add(pom.toString());
        command.add("org.openrewrite.maven:rewrite-maven-plugin:" + OpenRewriteMavenIntegrator.pluginVersion() + ":run");
        command.add("-Drewrite.activeRecipes=" + ACTIVE_RECIPE);
        command.add("-Drewrite.configLocation=.upgrd/openrewrite.yml");
        if (dryRun) {
            command.add("-Drewrite.dryRun=true");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(migrated.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String log = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(600, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new RewriteResult(false, -1, "OpenRewrite timed out after 600s", log);
        }

        int exit = process.exitValue();
        Path reportDir = migrated.resolve(".upgrd/rewrite");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("last-run.log"), log);

        return new RewriteResult(exit == 0, exit,
                dryRun ? "OpenRewrite dry-run completed" : "OpenRewrite apply completed",
                log);
    }

    public record RewriteResult(boolean success, int exitCode, String message, String log) {
    }
}
