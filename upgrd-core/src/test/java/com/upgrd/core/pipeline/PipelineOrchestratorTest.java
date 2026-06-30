package com.upgrd.core.pipeline;

import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.openrewrite.OpenRewriteRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void runsAnalyzePlanApplyWithoutVerify() throws Exception {
        Path fixture = Path.of(PipelineOrchestratorTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-backend")
                .toURI());
        Path source = copyFixture(fixture, tempDir.resolve("legacy"));
        Path output = tempDir.resolve("upgrd-out");

        var result = new PipelineOrchestrator().run(new PipelineOrchestrator.PipelineRequest(
                source,
                null,
                List.of(),
                output,
                ProjectProfile.LEGACY_BACKEND,
                "java21",
                "weblogic-14c",
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                OpenRewriteRunner.DEFAULT_RECIPE,
                false));

        assertTrue(result.completedPhases().containsAll(List.of("analyze", "plan", "apply")));
        assertTrue(Files.isRegularFile(output.resolve("migrated/pom.xml")));
        assertTrue(Files.isRegularFile(output.resolve("apply-report.json")));
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
