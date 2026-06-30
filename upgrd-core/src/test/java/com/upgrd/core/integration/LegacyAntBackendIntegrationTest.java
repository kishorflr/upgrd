package com.upgrd.core.integration;

import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyAntBackendIntegrationTest {

    private static Path fixtureRoot;

    @BeforeAll
    static void unpackFixture() throws Exception {
        fixtureRoot = Path.of(LegacyAntBackendIntegrationTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-backend")
                .toURI());
    }

    @TempDir
    Path tempDir;

    @Test
    void analyzePlanAndApplyLegacyBackend() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path output = tempDir.resolve("upgrd-out");

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_BACKEND);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);
        new UpgradePlanner().writePlan(plan, output);

        var applyReport = new ApplyEngine().apply(plan, source, output);

        long applied = applyReport.steps().stream().filter(s -> "APPLIED".equals(s.status())).count();
        long advisory = applyReport.steps().stream().filter(s -> "ADVISORY".equals(s.status())).count();
        assertTrue(applied >= 5, "expected multiple apply steps, got " + applied);
        assertTrue(advisory >= 2, "backend profile should keep design steps advisory");

        Path migratedJava = output.resolve("migrated/app-web/src/main/java/com/example/LegacyProcessor.java");
        assertTrue(Files.isRegularFile(migratedJava));
        String migrated = Files.readString(migratedJava);
        assertTrue(migrated.contains("ArrayList") || migrated.contains("List<Object>"),
                "raw/legacy collections should be upgraded");

        assertTrue(Files.isRegularFile(output.resolve("migrated/deploy/wildfly/jboss-web.xml")));
        assertTrue(Files.isRegularFile(output.resolve("migrated/AGENTS.md")));
    }

    private Path copyFixture(Path target) throws IOException {
        copyRecursive(fixtureRoot, target);
        return target;
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path dest = target.resolve(source.relativize(path));
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
    }
}
