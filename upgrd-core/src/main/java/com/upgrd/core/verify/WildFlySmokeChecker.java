package com.upgrd.core.verify;

import com.upgrd.core.wildfly.WildFlyCliService;
import com.upgrd.core.wildfly.WildFlyCliService.WildFlyResult;
import com.upgrd.core.wildfly.WildFlyCliService.WildFlyStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates WildFly deploy scaffolding and optionally stages a WAR via {@link WildFlyCliService}.
 */
public final class WildFlySmokeChecker {

    private final WildFlyCliService wildfly = new WildFlyCliService();

    public SmokeCheckResult check(Path outputDir) throws IOException, InterruptedException {
        WildFlyStatus status = wildfly.status(outputDir);
        return new SmokeCheckResult(
                status.scaffoldPresent(),
                status.dockerAvailable(),
                status.containerRunning(),
                status.warBuilt(),
                false,
                List.copyOf(status.notes()));
    }

    public SmokeCheckResult checkAndDeploy(Path outputDir, boolean deploy) throws IOException, InterruptedException {
        WildFlyStatus status = wildfly.status(outputDir);
        if (!deploy) {
            return new SmokeCheckResult(
                    status.scaffoldPresent(),
                    status.dockerAvailable(),
                    status.containerRunning(),
                    status.warBuilt(),
                    false,
                    List.copyOf(status.notes()));
        }

        List<String> notes = new ArrayList<>(status.notes());
        if (!status.scaffoldPresent()) {
            notes.add("Deploy skipped — WildFly scaffold missing");
            return new SmokeCheckResult(false, status.dockerAvailable(), false, false, false, notes);
        }

        WildFlyResult result = wildfly.deploy(outputDir, true);
        notes.add(result.message());
        WildFlyStatus after = wildfly.status(outputDir);
        notes.addAll(after.notes().stream().filter(n -> !notes.contains(n)).toList());

        return new SmokeCheckResult(
                after.scaffoldPresent(),
                after.dockerAvailable(),
                after.containerRunning(),
                after.warBuilt(),
                result.success(),
                List.copyOf(notes));
    }

    public record SmokeCheckResult(
            boolean scaffoldPresent,
            boolean dockerAvailable,
            boolean containerRunning,
            boolean warBuilt,
            boolean deployed,
            List<String> notes) {

        public boolean scaffoldComplete() {
            return scaffoldPresent;
        }
    }
}
