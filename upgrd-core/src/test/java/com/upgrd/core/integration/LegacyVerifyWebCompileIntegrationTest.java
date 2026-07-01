package com.upgrd.core.integration;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.WarConflictPolicy;
import com.upgrd.core.plan.PlanApprovalService;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.report.ReportWriter;
import com.upgrd.core.security.SecurityAnalyzer;
import com.upgrd.core.process.MavenCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compile-closure gate: Struts ActionForm in source must convert and {@code mvn test} must pass.
 */
class LegacyVerifyWebCompileIntegrationTest {

    private static Path fixtureRoot;

    @BeforeAll
    static void loadFixture() throws Exception {
        fixtureRoot = Path.of(LegacyVerifyWebCompileIntegrationTest.class.getClassLoader()
                .getResource("fixtures/legacy-verify-web")
                .toURI());
    }

    @TempDir
    Path tempDir;

    @Test
    void applyConvertsActionFormAndCompileClosurePasses() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createMinimalWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");

        new AnalyzeEngine().analyze(new AnalysisInput(source, war, List.of(), output, ProjectProfile.LEGACY_WEB));

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var reportWriter = new ReportWriter();
        var plan = new UpgradePlanner().plan(
                discovery, "java21", "weblogic-14c", false, security,
                reportWriter.readSyncReport(output),
                reportWriter.readUsageReport(output),
                reportWriter.readApiCompatibilityReport(output));
        new UpgradePlanner().writePlan(plan, output);

        var approval = new PlanApprovalService().createDefault(plan, source, true, true, false);
        new PlanApprovalService().write(approval, output);

        var applyReport = new ApplyEngine().apply(
                plan, source, output, approval,
                reportWriter.resolveWarApplyOptions(output, war, WarConflictPolicy.MARK_CONFLICT));

        assertTrue(applyReport.steps().stream()
                .anyMatch(s -> "compile-closure".equals(s.stepId())));

        Path loginForm = output.resolve("migrated/app-web/src/main/java/com/demo/verify/LoginForm.java");
        assertTrue(Files.isRegularFile(loginForm));
        String formSource = Files.readString(loginForm);
        assertFalse(formSource.contains("ActionForm"));

        assertTrue(applyReport.steps().stream()
                .filter(s -> "compile-closure".equals(s.stepId()))
                .anyMatch(s -> s.message().contains("mvn compile: PASSED")));
    }

    @Test
    @EnabledIf("com.upgrd.core.process.MavenCommand#isAvailable")
    void migratedProjectPassesMvnTest() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createMinimalWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");

        new AnalyzeEngine().analyze(new AnalysisInput(source, war, List.of(), output, ProjectProfile.LEGACY_WEB));
        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);
        var approval = new PlanApprovalService().createDefault(plan, source, true, true, false);
        new ApplyEngine().apply(plan, source, output, approval);

        Path migratedPom = output.resolve("migrated/pom.xml");
        assumeTrue(Files.isRegularFile(migratedPom));

        ProcessBuilder builder = new ProcessBuilder(MavenCommand.executable(), "-f", migratedPom.toString(), "test", "-q");
        builder.directory(output.resolve("migrated").toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String log = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor() == 0, "mvn test failed:\n" + log);
    }
    private Path copyFixture(Path target) throws Exception {
        Files.walk(fixtureRoot).forEach(source -> {
            try {
                Path dest = target.resolve(fixtureRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(source, dest);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        return target;
    }

    private Path createMinimalWar(Path warPath) throws Exception {
        Files.createDirectories(warPath.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(warPath))) {
            zos.putNextEntry(new ZipEntry("WEB-INF/"));
            zos.closeEntry();
            writeEntry(zos, "WEB-INF/web.xml", Files.readString(fixtureRoot.resolve("WEB-INF/web.xml")));
            writeEntry(zos, "WEB-INF/classes/com/demo/verify/LoginAction.class", new byte[] {0});
        }
        return warPath;
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private void writeEntry(ZipOutputStream zos, String name, String text) throws Exception {
        writeEntry(zos, name, text.getBytes());
    }
}
