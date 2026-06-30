package com.upgrd.core.integration;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.apply.ApplyEngine;
import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.plan.UpgradePlanner;
import com.upgrd.core.security.SecurityAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LegacyAntWebIntegrationTest {

    private static Path fixtureRoot;

    @BeforeAll
    static void unpackFixture() throws Exception {
        fixtureRoot = Path.of(LegacyAntWebIntegrationTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-web")
                .toURI());
    }

    @TempDir
    Path tempDir;

    @Test
    void analyzePlanAndApplyLegacyAntWeb() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createMinimalWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");

        AnalyzeEngine analyzeEngine = new AnalyzeEngine();
        var analysis = analyzeEngine.analyze(new AnalysisInput(source, war, List.of(), output, ProjectProfile.LEGACY_WEB));

        assertTrue(analysis.discovery().profile() == ProjectProfile.LEGACY_WEB
                || analysis.discovery().fingerprint().frameworks().stream().anyMatch(f -> f.contains("STRUTS")));

        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        UpgradePlanner planner = new UpgradePlanner();
        var plan = planner.plan(discovery, "java21", "weblogic-14c", false, security);
        planner.writePlan(plan, output);

        ApplyEngine applyEngine = new ApplyEngine();
        var applyReport = applyEngine.apply(plan, source, output);

        long applied = applyReport.steps().stream().filter(s -> "APPLIED".equals(s.status())).count();
        assertTrue(applied >= 4, "expected multiple apply steps, got " + applied);

        Path migratedAction = output.resolve("migrated/app-web/src/main/java/com/example/UserAction.java");
        assertTrue(Files.isRegularFile(migratedAction));
        String migrated = Files.readString(migratedAction);
        assertTrue(migrated.contains("@Controller"), "Struts should become Spring controller");
        assertTrue(migrated.contains("jakarta.servlet") || migrated.contains("LoggerFactory"),
                "javax/logging migrations expected");

        assertTrue(Files.isRegularFile(output.resolve("migrated/AGENTS.md")));
        assertTrue(Files.isRegularFile(output.resolve("migrated/upgrd-analysis.json")));
        assertTrue(Files.exists(output.resolve("migrated/app-web/src/test/java/com/upgrd/smoke")));
        assertTrue(Files.isRegularFile(
                output.resolve("migrated/app-web/src/main/webapp/WEB-INF/spring-struts-migration.xml")),
                "Struts config should produce Spring migration hints");
        assertTrue(
                Files.isRegularFile(output.resolve("migrated/app-web/src/main/webapp/pages/login.jsp.struts-view-hints.md"))
                        || Files.isRegularFile(output.resolve(
                        "migrated/app-web/src/main/webapp/WEB-INF/validation.xml.struts-view-hints.md")),
                "Struts JSP/validation should produce view migration hints");
        assertTrue(Files.isRegularFile(
                output.resolve("migrated/app-web/src/main/resources/templates/pages/login.html")),
                "Struts JSP should scaffold Thymeleaf template");
    }

    @Test
    @EnabledIf("mavenAvailable")
    void verifyRunsMigratedTests() throws Exception {
        Path source = copyFixture(tempDir.resolve("legacy"));
        Path war = createMinimalWar(tempDir.resolve("legacy.war"));
        Path output = tempDir.resolve("upgrd-out");

        new AnalyzeEngine().analyze(new AnalysisInput(source, war, List.of(), output, ProjectProfile.LEGACY_WEB));
        var discovery = new ProjectDiscoveryService().discover(source, ProjectProfile.LEGACY_WEB);
        var security = new SecurityAnalyzer().analyze(source, discovery);
        var plan = new UpgradePlanner().plan(discovery, "java21", "weblogic-14c", false, security);
        new ApplyEngine().apply(plan, source, output);

        Path migratedPom = output.resolve("migrated/pom.xml");
        assumeTrue(Files.isRegularFile(migratedPom));

        ProcessBuilder builder = new ProcessBuilder("mvn", "-f", migratedPom.toString(), "test", "-q");
        builder.directory(output.resolve("migrated").toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String mvnOutput = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        assertTrue(exit == 0, "mvn test failed with exit " + exit + ":\n" + mvnOutput);
    }

    static boolean mavenAvailable() {
        try {
            return new ProcessBuilder("mvn", "-version").start().waitFor() == 0;
        } catch (Exception ex) {
            return false;
        }
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

    private Path createMinimalWar(Path warPath) throws IOException {
        Files.createDirectories(warPath.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(warPath))) {
            zos.putNextEntry(new ZipEntry("WEB-INF/"));
            zos.closeEntry();
            writeEntry(zos, "WEB-INF/web.xml", Files.readString(fixtureRoot.resolve("WEB-INF/web.xml")));
            writeEntry(zos, "WEB-INF/classes/com/example/UserAction.class", new byte[] {0});
        }
        return warPath;
    }

    private void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private void writeEntry(ZipOutputStream zos, String name, String text) throws IOException {
        writeEntry(zos, name, text.getBytes());
    }
}
