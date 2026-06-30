package com.upgrd.core.wildfly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Edge-local WildFly lifecycle: build WAR, Docker compose, and hot deployment.
 */
public final class WildFlyCliService {

    public static final String CONTAINER_NAME = "upgrd-wildfly";
    public static final String WAR_ARTIFACT = "app-web-1.0.0-SNAPSHOT.war";
    public static final String DEPLOYMENT_NAME = "app-web.war";

    private final ProcessRunner processes = new ProcessRunner();

    public WildFlyStatus status(Path outputDir) throws IOException, InterruptedException {
        Path migrated = migratedRoot(outputDir);
        Path wildflyDir = migrated.resolve("deploy/wildfly");
        List<String> notes = new ArrayList<>();

        boolean scaffoldOk = Files.isRegularFile(wildflyDir.resolve("docker-compose.yml"));
        notes.add("Scaffold: " + (scaffoldOk ? "present" : "missing — run upgrd apply first"));

        boolean docker = processes.available("docker");
        notes.add("Docker CLI: " + (docker ? "available" : "not found"));

        boolean running = docker && isContainerRunning();
        notes.add("Container " + CONTAINER_NAME + ": " + (running ? "running" : "stopped"));

        Path war = warPath(migrated);
        notes.add("WAR built: " + (Files.isRegularFile(war) ? war : "not yet — run upgrd wildfly deploy --build"));

        Path deployed = wildflyDir.resolve("deployments").resolve(DEPLOYMENT_NAME);
        notes.add("Deployed copy: " + (Files.isRegularFile(deployed) ? deployed.getFileName() : "none"));

        return new WildFlyStatus(scaffoldOk, docker, running, Files.isRegularFile(war), notes);
    }

    public WildFlyResult start(Path outputDir) throws IOException, InterruptedException {
        Path migrated = migratedRoot(outputDir);
        Path wildflyDir = migrated.resolve("deploy/wildfly");
        Path compose = composeFile(outputDir);
        int exit = processes.run(List.of("docker", "compose", "-f", compose.toString(), "up", "-d"),
                wildflyDir, 120);
        return new WildFlyResult(exit == 0, exit, exit == 0 ? "WildFly container started" : "Failed to start WildFly");
    }

    public WildFlyResult stop(Path outputDir) throws IOException, InterruptedException {
        Path compose = composeFile(outputDir);
        int exit = processes.run(List.of("docker", "compose", "-f", compose.toString(), "down"),
                migratedRoot(outputDir), 60);
        return new WildFlyResult(exit == 0, exit, exit == 0 ? "WildFly container stopped" : "Failed to stop WildFly");
    }

    public WildFlyResult deploy(Path outputDir, boolean build) throws IOException, InterruptedException {
        Path migrated = migratedRoot(outputDir);
        Path wildflyDir = migrated.resolve("deploy/wildfly");
        Files.createDirectories(wildflyDir.resolve("deployments"));

        if (build) {
            int buildExit = processes.run(
                    List.of("mvn", "-f", migrated.resolve("pom.xml").toString(), "-Plocal-wildfly", "package", "-q"),
                    migrated,
                    300);
            if (buildExit != 0) {
                return new WildFlyResult(false, buildExit, "Maven package failed (exit " + buildExit + ")");
            }
        }

        Path war = warPath(migrated);
        if (!Files.isRegularFile(war)) {
            return new WildFlyResult(false, 1, "WAR not found: " + war + " — use --build");
        }

        Path deploymentCopy = wildflyDir.resolve("deployments").resolve(DEPLOYMENT_NAME);
        Files.copy(war, deploymentCopy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String msg = isContainerRunning()
                ? "Deployed " + DEPLOYMENT_NAME + " via deployments volume (context /app-web)"
                : "WAR staged at " + deploymentCopy + " — run: upgrd wildfly start";
        return new WildFlyResult(true, 0, msg);
    }

    public WildFlyResult undeploy(Path outputDir) throws IOException, InterruptedException {
        Path migrated = migratedRoot(outputDir);
        Path deploymentCopy = migrated.resolve("deploy/wildfly/deployments").resolve(DEPLOYMENT_NAME);
        Files.deleteIfExists(deploymentCopy);

        if (isContainerRunning()) {
            processes.run(List.of(
                    "docker", "exec", CONTAINER_NAME, "rm", "-f",
                    "/opt/jboss/wildfly/standalone/deployments/" + DEPLOYMENT_NAME),
                    migrated,
                    30);
            processes.run(List.of(
                    "docker", "exec", CONTAINER_NAME, "rm", "-f",
                    "/opt/jboss/wildfly/standalone/deployments/" + DEPLOYMENT_NAME + ".deployed"),
                    migrated,
                    30);
        }
        return new WildFlyResult(true, 0, "Undeployed " + DEPLOYMENT_NAME);
    }

    public boolean isContainerRunning() throws IOException, InterruptedException {
        int exit = processes.run(
                List.of("docker", "ps", "--filter", "name=" + CONTAINER_NAME, "--filter", "status=running", "-q"),
                Path.of("."),
                15);
        return exit == 0 && !processes.lastOutput().isBlank();
    }

    private Path composeFile(Path outputDir) throws IOException {
        Path compose = migratedRoot(outputDir).resolve("deploy/wildfly/docker-compose.yml");
        if (!Files.isRegularFile(compose)) {
            throw new IOException("WildFly compose file not found: " + compose);
        }
        return compose;
    }

    private Path migratedRoot(Path outputDir) {
        return outputDir.resolve("migrated").toAbsolutePath().normalize();
    }

    private Path warPath(Path migrated) {
        return migrated.resolve("app-web/target").resolve(WAR_ARTIFACT);
    }

    public record WildFlyStatus(
            boolean scaffoldPresent,
            boolean dockerAvailable,
            boolean containerRunning,
            boolean warBuilt,
            List<String> notes) {
    }

    public record WildFlyResult(boolean success, int exitCode, String message) {
    }

    static final class ProcessRunner {
        private String lastOutput = "";

        String lastOutput() {
            return lastOutput;
        }

        boolean available(String command) {
            try {
                return run(List.of(command, "--version"), Path.of("."), 10) == 0
                        || run(List.of(command, "version"), Path.of("."), 10) == 0;
            } catch (Exception ex) {
                return false;
            }
        }

        int run(List<String> command, Path workDir, int timeoutSeconds) throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workDir.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            lastOutput = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Command timed out: " + String.join(" ", command));
            }
            return process.exitValue();
        }
    }
}
