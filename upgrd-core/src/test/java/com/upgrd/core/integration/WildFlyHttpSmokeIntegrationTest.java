package com.upgrd.core.integration;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.security.SecurityAnalyzer;
import com.upgrd.core.wildfly.WildFlyCliService;
import com.upgrd.core.wildfly.WildFlyHttpProber;
import com.upgrd.core.verify.WildFlySmokeChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end WildFly Docker deploy + live HTTP probe (runs in CI when Docker is available).
 */
class WildFlyHttpSmokeIntegrationTest {

    private static Path fixtureRoot;
    private Path output;

    @BeforeAll
    static void locateFixture() throws Exception {
        fixtureRoot = Path.of(WildFlyHttpSmokeIntegrationTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-web")
                .toURI());
    }

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() throws Exception {
        if (output != null) {
            new WildFlyCliService().stop(output);
        }
    }

    @Test
    @EnabledIf("dockerAvailable")
    void deploysWarAndRespondsOverHttp() throws Exception {
        output = tempDir.resolve("upgrd-out");
        Path source = copyFixture(tempDir.resolve("legacy"));

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);
        new ApplyEngine().apply(plan, source, output);

        var start = new WildFlyCliService().start(output);
        assertTrue(start.success(), start.message());

        var smoke = new WildFlySmokeChecker().checkDeployAndHttp(output, true, true);
        assertTrue(smoke.deployed(), "WAR should be staged/deployed: " + smoke.notes());
        assertTrue(smoke.httpChecked(), "HTTP probe should run when container is up");
        assertTrue(smoke.httpReachable(),
                "Expected HTTP response from WildFly at " + smoke.httpUrl() + " — notes: " + smoke.notes());

        var probe = new WildFlyHttpProber().probeOnce(smoke.httpUrl());
        assertTrue(probe.statusCode() >= 200 && probe.statusCode() < 500,
                "HTTP status should be 2xx–4xx, got " + probe.statusCode());
    }

    static boolean dockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info").start();
            return process.waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private Path copyFixture(Path target) throws IOException {
        Files.walk(fixtureRoot).forEach(path -> {
            try {
                Path dest = target.resolve(fixtureRoot.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        return target;
    }
}
