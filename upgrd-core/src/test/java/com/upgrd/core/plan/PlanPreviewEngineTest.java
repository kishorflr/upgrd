package com.upgrd.core.plan;

import com.upgrd.core.model.ChangeClassification;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.UpgradePlan;
import com.upgrd.core.model.UpgradeStep;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanPreviewEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void previewGeneratesBeforeAfterForLog4jOnFlatLayout() throws Exception {
        Path source = tempDir.resolve("legacy");
        Path src = source.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("UserAction.java"), """
                import org.apache.log4j.Logger;
                public class UserAction {
                    private static Logger log = Logger.getLogger(UserAction.class);
                }
                """);

        var discovery = new com.upgrd.core.discovery.ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        UpgradePlan plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", true, security);

        var preview = new PlanPreviewEngine().preview(plan, source);

        assertTrue(preview.previewedFileChanges() >= 1 || preview.changes().stream()
                .anyMatch(c -> c.before() != null && c.before().contains("log4j")));
        assertTrue(preview.steps().stream()
                .anyMatch(s -> s.classification() == ChangeClassification.MANDATORY));
    }

    @Test
    void pipelineStopsAfterPreviewWithoutConfirm() throws Exception {
        Path fixture = Path.of(PlanPreviewEngineTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-backend")
                .toURI());
        Path source = copyFixture(fixture, tempDir.resolve("legacy"));
        Path output = tempDir.resolve("out");

        var result = new com.upgrd.core.pipeline.PipelineOrchestrator().run(
                new com.upgrd.core.pipeline.PipelineOrchestrator.PipelineRequest(
                        source, null, List.of(), output, ProjectProfile.LEGACY_BACKEND,
                        "java21", "weblogic-14c", false, false,
                        false, false, false, false, true,
                        false, false, null, false));

        assertTrue(result.success());
        assertTrue(result.completedPhases().contains("preview"));
        assertFalse(result.completedPhases().contains("apply"));
        assertTrue(Files.isRegularFile(output.resolve("upgrade-preview-report.json")));
        assertTrue(Files.isRegularFile(output.resolve("change-ledger-preview.json")));
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
