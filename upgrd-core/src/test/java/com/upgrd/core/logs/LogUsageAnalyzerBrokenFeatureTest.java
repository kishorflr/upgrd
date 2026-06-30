package com.upgrd.core.logs;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.FeatureHealth;
import com.upgrd.core.model.LogKind;
import com.upgrd.core.model.LogSourceEntry;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.usage.FeatureUsageAnalyzer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUsageAnalyzerBrokenFeatureTest {

    @Test
    void detectsBrokenHttpPathAndClassErrors() throws Exception {
        Path access = Files.createTempFile("access", ".log");
        Path server = Files.createTempFile("server", ".log");
        Files.writeString(access, """
                GET /pages/broken.jsp HTTP/1.1" 500 1200
                GET /pages/ok.jsp HTTP/1.1" 200 800
                """);
        Files.writeString(server, """
                ERROR com.example.WarOnlyAction - request failed
                java.lang.RuntimeException: boom
                    at com.example.WarOnlyAction.process(WarOnlyAction.java:12)
                """);

        var usage = new LogUsageAnalyzer().analyze(
                List.of(access, server),
                Set.of("com.example.WarOnlyAction", "com.example.UserAction"),
                List.of(
                        new LogSourceEntry(access.getFileName().toString(), "access.log", "test", LogKind.ACCESS, 1),
                        new LogSourceEntry(server.getFileName().toString(), "server.log", "test", LogKind.SERVER, 1)));

        assertTrue(usage.brokenSignals().stream().anyMatch(s -> s.pathOrClass().contains("broken.jsp")));
        assertTrue(usage.brokenSignals().stream().anyMatch(s -> s.pathOrClass().contains("WarOnlyAction")));

        Path fixture = Path.of(LogUsageAnalyzerBrokenFeatureTest.class.getClassLoader()
                .getResource("fixtures/legacy-e2e-web")
                .toURI());
        var discovery = new ProjectDiscoveryService().discover(fixture, ProjectProfile.LEGACY_WEB);
        var featureReport = new FeatureUsageAnalyzer().analyze(
                fixture, discovery, null, usage,
                Set.of("com.example.WarOnlyAction"), Set.of("com.example.UserAction"));

        assertTrue(featureReport.brokenCount() > 0);
        assertTrue(featureReport.features().stream().anyMatch(f -> f.health() == FeatureHealth.BROKEN));
    }
}
