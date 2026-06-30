package com.upgrd.core.compat;

import com.upgrd.core.discovery.ProjectDiscoveryService;
import com.upgrd.core.model.ApiRemediationType;
import com.upgrd.core.model.ProjectProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCompatibilityAnalyzerTest {

    @Test
    void detectsJavaxServletAndLog4jInLegacyWebFixture() throws Exception {
        Path fixture = Path.of(ApiCompatibilityAnalyzerTest.class.getClassLoader()
                .getResource("fixtures/legacy-ant-web")
                .toURI());

        var discovery = new ProjectDiscoveryService().discover(fixture, ProjectProfile.LEGACY_WEB);
        var report = new ApiCompatibilityAnalyzer().analyze(fixture, discovery);

        assertTrue(report.totalHits() >= 2);
        assertTrue(report.hits().stream().anyMatch(h -> h.api().contains("javax.servlet")));
        assertTrue(report.hits().stream().anyMatch(h -> h.api().contains("log4j")));
        assertTrue(report.hits().stream()
                .anyMatch(h -> h.remediationType() == ApiRemediationType.REPLACEMENT));
        assertTrue(report.hits().stream()
                .anyMatch(h -> "portable-jakarta".equals(h.planStepId())
                        || "migrate-log4j1".equals(h.planStepId())));
    }
}
