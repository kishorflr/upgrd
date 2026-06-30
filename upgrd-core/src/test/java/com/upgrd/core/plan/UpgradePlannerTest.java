package com.upgrd.core.plan;

import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.LoggingFramework;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.ServletApi;
import com.upgrd.core.model.StepMode;
import com.upgrd.core.model.TechnologyFingerprint;
import com.upgrd.core.model.UpgradePlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradePlannerTest {

    private final UpgradePlanner planner = new UpgradePlanner();

    @Test
    void legacyWebPlanIncludesFrameworkSteps() {
        ProjectDiscovery discovery = legacyWebDiscovery();
        UpgradePlan plan = planner.plan(discovery, "java21", "weblogic-14c", true, emptySecurity());

        assertEquals(ProjectProfile.LEGACY_WEB, plan.profile());
        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("migrate-log4j1")));
        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("spring-4-to-6")));
        assertTrue(plan.steps().stream().allMatch(s -> s.reason() != null && !s.reason().isBlank()));
    }

    @Test
    void legacyBackendPlanIncludesAdvisorySteps() {
        ProjectDiscovery discovery = legacyBackendDiscovery();
        UpgradePlan plan = planner.plan(discovery, "java21", "weblogic-14c", true, emptySecurity());

        assertEquals(ProjectProfile.LEGACY_BACKEND, plan.profile());
        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("introduce-layering")
                && s.mode() == StepMode.ADVISORY));
        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("add-interfaces")
                && s.mode() == StepMode.ADVISORY));
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
                        List.of("classpath:log4j-1.2.17.jar")),
                ProjectProfile.LEGACY_WEB);
    }

    private ProjectDiscovery legacyBackendDiscovery() {
        return new ProjectDiscovery(
                BuildSystem.ANT,
                "1.7",
                List.of("src"),
                List.of(),
                false,
                new TechnologyFingerprint(
                        List.of(),
                        LoggingFramework.JUL,
                        ServletApi.NONE,
                        "JDBC",
                        List.of("god-class", "raw-collections"),
                        List.of("LegacyService.java: 520 lines")),
                ProjectProfile.LEGACY_BACKEND);
    }

    private com.upgrd.core.model.SecurityReport emptySecurity() {
        return new com.upgrd.core.model.SecurityReport(
                "test", Instant.now(), ProjectProfile.UNKNOWN, List.of(), 0, 0);
    }
}
