package com.upgrd.core.verify;

import com.upgrd.core.wildfly.WildFlyCliService;
import com.upgrd.core.wildfly.WildFlyCliService.WildFlyResult;
import com.upgrd.core.wildfly.WildFlyCliService.WildFlyStatus;
import com.upgrd.core.wildfly.WildFlyHttpProber;
import com.upgrd.core.wildfly.WildFlyHttpProber.HttpProbeResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates WildFly deploy scaffolding and optionally stages a WAR via {@link WildFlyCliService}.
 */
public final class WildFlySmokeChecker {

    private static final int HTTP_ATTEMPTS = 12;
    private static final long HTTP_DELAY_MS = 5_000;

    private final WildFlyCliService wildfly = new WildFlyCliService();
    private final WildFlyHttpProber httpProber = new WildFlyHttpProber();

    public SmokeCheckResult check(Path outputDir) throws IOException, InterruptedException {
        return finish(wildfly.status(outputDir), false, false, outputDir);
    }

    public SmokeCheckResult checkAndDeploy(Path outputDir, boolean deploy) throws IOException, InterruptedException {
        return checkDeployAndHttp(outputDir, deploy, false);
    }

    public SmokeCheckResult checkDeployAndHttp(Path outputDir, boolean deploy, boolean httpProbe)
            throws IOException, InterruptedException {
        WildFlyStatus status = wildfly.status(outputDir);
        if (!deploy) {
            return finish(status, false, httpProbe, outputDir);
        }

        List<String> notes = new ArrayList<>(status.notes());
        if (!status.scaffoldPresent()) {
            notes.add("Deploy skipped — WildFly scaffold missing");
            return resultFrom(status, false, notes, null);
        }

        WildFlyResult result = wildfly.deploy(outputDir, true);
        notes.add(result.message());
        WildFlyStatus after = wildfly.status(outputDir);
        notes.addAll(after.notes().stream().filter(n -> !notes.contains(n)).toList());

        return finish(after, result.success(), httpProbe, outputDir, notes);
    }

    private SmokeCheckResult finish(WildFlyStatus status, boolean deployed, boolean httpProbe, Path outputDir)
            throws IOException, InterruptedException {
        return finish(status, deployed, httpProbe, outputDir, new ArrayList<>(status.notes()));
    }

    private SmokeCheckResult finish(
            WildFlyStatus status,
            boolean deployed,
            boolean httpProbe,
            Path outputDir,
            List<String> notes) throws IOException, InterruptedException {
        HttpProbeResult http = null;
        if (httpProbe) {
            if (status.containerRunning()) {
                notes.add("HTTP smoke: probing localhost (WildFly may need time to deploy WAR)…");
                http = httpProber.probe(outputDir, WildFlyHttpProber.DEFAULT_PORT, HTTP_ATTEMPTS, HTTP_DELAY_MS);
                notes.add(http.message());
            } else {
                notes.add("HTTP smoke skipped — container not running (upgrd wildfly start)");
            }
        }
        return resultFrom(status, deployed, notes, http);
    }

    private SmokeCheckResult resultFrom(
            WildFlyStatus status,
            boolean deployed,
            List<String> notes,
            HttpProbeResult http) {
        return new SmokeCheckResult(
                status.scaffoldPresent(),
                status.dockerAvailable(),
                status.containerRunning(),
                status.warBuilt(),
                deployed,
                http != null && http.checked(),
                http != null && http.reachable(),
                http == null ? 0 : http.statusCode(),
                http == null ? null : http.url(),
                List.copyOf(notes));
    }

    public record SmokeCheckResult(
            boolean scaffoldPresent,
            boolean dockerAvailable,
            boolean containerRunning,
            boolean warBuilt,
            boolean deployed,
            boolean httpChecked,
            boolean httpReachable,
            int httpStatusCode,
            String httpUrl,
            List<String> notes) {

        public boolean scaffoldComplete() {
            return scaffoldPresent;
        }
    }
}
