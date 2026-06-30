package com.upgrd.core.plan;

import com.upgrd.core.model.ApiCompatibilityHit;
import com.upgrd.core.model.ApiCompatibilityReport;
import com.upgrd.core.model.ApiRemediationType;
import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.LoggingFramework;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.ServletApi;
import com.upgrd.core.model.TechnologyFingerprint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradePlannerApiCompatTest {

    private final UpgradePlanner planner = new UpgradePlanner();

    @Test
    void linksCatalogHitsToPlanStepsAndAddsManualAdvisory() {
        ApiCompatibilityReport api = new ApiCompatibilityReport(
                "test",
                Instant.now(),
                2,
                Map.of(
                        ApiRemediationType.REPLACEMENT, 1,
                        ApiRemediationType.MANUAL, 1,
                        ApiRemediationType.AUTOMATED, 0,
                        ApiRemediationType.UNSUPPORTED, 0),
                List.of(
                        new ApiCompatibilityHit(
                                "javax-servlet-0001", "javax-servlet", "javax.servlet",
                                ApiRemediationType.REPLACEMENT, "src/App.java", List.of(3, 3),
                                "import javax.servlet.http.HttpServletRequest;",
                                "jakarta.servlet.*", "Jakarta migration", "upgrd:JavaxToJakarta",
                                "portable-jakarta"),
                        new ApiCompatibilityHit(
                                "struts-action-0001", "struts-action", "org.apache.struts.action",
                                ApiRemediationType.MANUAL, "src/App.java", List.of(5, 5),
                                "extends Action",
                                "Spring MVC @Controller", "Struts migration", null,
                                "struts-to-spring-mvc")));

        var plan = planner.plan(
                legacyWebDiscovery(),
                "java21",
                "weblogic-14c",
                true,
                emptySecurity(),
                null,
                null,
                api);

        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("api-manual-rewrite")));
        assertTrue(plan.steps().stream()
                .filter(s -> s.id().equals("portable-jakarta"))
                .anyMatch(s -> s.evidence().stream().anyMatch(e -> e.contains("javax.servlet"))));
    }

    private ProjectDiscovery legacyWebDiscovery() {
        return new ProjectDiscovery(
                BuildSystem.ANT,
                "1.8",
                List.of("src"),
                List.of("WEB-INF/web.xml"),
                false,
                new TechnologyFingerprint(
                        List.of("SPRING_MVC_4", "STRUTS_1"),
                        LoggingFramework.LOG4J_1,
                        ServletApi.JAVAX,
                        "JDBC",
                        List.of(),
                        List.of()),
                ProjectProfile.LEGACY_WEB);
    }

    private com.upgrd.core.model.SecurityReport emptySecurity() {
        return new com.upgrd.core.model.SecurityReport(
                "test", Instant.now(), ProjectProfile.UNKNOWN, List.of(), 0, 0);
    }
}
