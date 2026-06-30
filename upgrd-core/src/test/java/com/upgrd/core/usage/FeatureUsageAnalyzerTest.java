package com.upgrd.core.usage;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.logs.LogUsageAnalyzer;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.UsageObservation;
import com.upgrd.core.source.SourceInspector;
import com.upgrd.core.sync.SyncAnalyzer;
import com.upgrd.core.war.WarInspector;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureUsageAnalyzerTest {

    @Test
    void marksObservedAndUnobservedFeaturesFromLogsAndInventory() throws Exception {
        Path fixture = Path.of(FeatureUsageAnalyzerTest.class.getClassLoader()
                .getResource("fixtures/legacy-e2e-web")
                .toURI());
        Path war = java.nio.file.Files.createTempFile("feature-usage", ".war");
        try (ZipOutputStream zos = new ZipOutputStream(java.nio.file.Files.newOutputStream(war))) {
            zos.putNextEntry(new ZipEntry("WEB-INF/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("WEB-INF/classes/com/example/UserAction.class"));
            zos.write(new byte[] {0});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("WEB-INF/classes/com/example/WarOnlyAction.class"));
            zos.write(new byte[] {0});
            zos.closeEntry();
        }

        var discovery = new ProjectDiscoveryService().discover(fixture, ProjectProfile.LEGACY_WEB);
        Set<String> warClasses = new WarInspector().listApplicationClasses(war);
        Set<String> sourceClasses = new SourceInspector().listSourceClasses(fixture, discovery.sourceRoots());
        var sync = new SyncAnalyzer().compare(warClasses, sourceClasses, Set.of(), Set.of());

        List<Path> logs = List.of(
                fixture.resolve("logs/access.log"),
                fixture.resolve("logs/server.log"));
        var usage = new LogUsageAnalyzer().analyze(logs, warClasses);

        var report = new FeatureUsageAnalyzer().analyze(
                fixture, discovery, sync, usage, warClasses, sourceClasses);

        assertTrue(report.totalFeatures() > 0);
        assertTrue(report.observedCount() > 0, "UserAction should be observed");
        assertTrue(report.features().stream().anyMatch(f ->
                "com.example.UserAction".equals(f.name())
                        && f.observation() == UsageObservation.OBSERVED));
        assertTrue(report.features().stream().anyMatch(f ->
                "com.example.WarOnlyAction".equals(f.name())
                        && f.observation() == UsageObservation.OBSERVED));
        assertTrue(report.features().stream().anyMatch(f ->
                f.name().contains("success.jsp")
                        && f.health() == com.upgrd.core.model.FeatureHealth.UNOBSERVED));
        assertTrue(report.features().stream().anyMatch(f ->
                f.kind().name().equals("STRUTS_ACTION")
                        && "/user".equals(f.name())));
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("absence in logs")));
    }

    @Test
    void allUnobservedWhenNoLogsProvided() throws Exception {
        Path fixture = Path.of(FeatureUsageAnalyzerTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-web")
                .toURI());
        var discovery = new ProjectDiscoveryService().discover(fixture, ProjectProfile.LEGACY_WEB);
        Set<String> sourceClasses = new SourceInspector().listSourceClasses(fixture, discovery.sourceRoots());
        var usage = new LogUsageAnalyzer().analyze(List.of(), Set.of());

        var report = new FeatureUsageAnalyzer().analyze(
                fixture, discovery, null, usage, Set.of(), sourceClasses);

        assertEquals(0, report.observedCount());
        assertTrue(report.unobservedCount() > 0);
        assertTrue(report.features().stream()
                .allMatch(f -> f.observation() == UsageObservation.UNOBSERVED));
    }
}
