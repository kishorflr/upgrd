package com.upgrd.core.apply;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyWebApplyStepsTest {

    @TempDir
    Path tempDir;

    @Test
    void strutsStepsProduceExpectedArtifacts() throws Exception {
        Path fixture = Path.of(LegacyWebApplyStepsTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-web")
                .toURI());
        Path source = copyFixture(fixture, tempDir.resolve("legacy"));
        Path output = tempDir.resolve("out");

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);
        var report = new ApplyEngine().apply(plan, source, output);

        String steps = report.steps().stream()
                .map(s -> s.stepId() + "=" + s.status())
                .collect(Collectors.joining(", "));
        assertTrue(steps.contains("struts-config-to-spring=APPLIED"), "steps: " + steps);

        Path hints = output.resolve("migrated/app-web/src/main/webapp/WEB-INF/spring-struts-migration.xml");
        assertTrue(Files.isRegularFile(hints), "missing hints; steps=" + steps);

        Path userForm = output.resolve("migrated/app-web/src/main/java/com/example/UserForm.java");
        assertTrue(Files.isRegularFile(userForm), "form bean should be scaffolded");

        Path userAction = output.resolve("migrated/app-web/src/main/java/com/example/UserAction.java");
        String action = Files.readString(userAction);
        assertTrue(action.contains("return \"pages/success\""), action);
        assertTrue(action.contains("UserForm"), action);
    }

    private Path copyFixture(Path fixtureRoot, Path target) throws Exception {
        Files.walk(fixtureRoot).forEach(path -> {
            try {
                Path dest = target.resolve(fixtureRoot.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        return target;
    }
}
