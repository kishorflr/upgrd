package com.upgrd.core.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates WildFly local deploy scaffolding (M5+ smoke check, no container required).
 */
public final class WildFlySmokeChecker {

    public SmokeCheckResult check(Path migratedRoot) throws IOException {
        Path wildflyDir = migratedRoot.resolve("deploy/wildfly");
        List<String> notes = new ArrayList<>();
        boolean ok = true;

        for (String file : List.of("docker-compose.yml", "jboss-web.xml", "deploy.sh")) {
            Path path = wildflyDir.resolve(file);
            if (Files.isRegularFile(path)) {
                notes.add("Found " + path.getFileName());
            } else {
                notes.add("Missing " + file);
                ok = false;
            }
        }

        if (dockerAvailable()) {
            notes.add("Docker CLI available — run: docker compose -f deploy/wildfly/docker-compose.yml up -d");
        } else {
            notes.add("Docker not detected — WildFly smoke deploy skipped (scaffold files only)");
        }

        return new SmokeCheckResult(ok, notes);
    }

    private boolean dockerAvailable() {
        try {
            return new ProcessBuilder("docker", "version").start().waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public record SmokeCheckResult(boolean scaffoldComplete, List<String> notes) {
    }
}
