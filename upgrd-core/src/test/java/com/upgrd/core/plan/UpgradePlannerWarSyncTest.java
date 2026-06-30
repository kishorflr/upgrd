package com.upgrd.core.plan;

import com.upgrd.core.model.BuildSystem;
import com.upgrd.core.model.LoggingFramework;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.ProjectProfile;
import com.upgrd.core.model.ServletApi;
import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.SyncSeverity;
import com.upgrd.core.model.TechnologyFingerprint;
import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradePlannerWarSyncTest {

    private final UpgradePlanner planner = new UpgradePlanner();

    @Test
    void addsWarSyncStepsWhenDriftDetected() {
        SyncReport sync = new SyncReport(
                5, 3,
                List.of("com.prod.OnlyInWar"),
                List.of("com.dev.OnlyInSource"),
                List.of("com.shared.Both"),
                2, 1,
                List.of("prod-only.jar"),
                List.of(),
                List.of("shared.jar"),
                SyncSeverity.HIGH,
                "Production WAR differs from source");

        UsageReport usage = new UsageReport(
                1, 10,
                List.of(new UsageHit("com.prod.OnlyInWar", 42, "sample")),
                List.of());

        var plan = planner.plan(
                legacyWebDiscovery(),
                "java21",
                "weblogic-14c",
                true,
                emptySecurity(),
                sync,
                usage);

        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("war-source-sync")));
        assertTrue(plan.steps().stream().anyMatch(s -> s.id().equals("war-lib-align")));
        assertTrue(plan.steps().stream()
                .filter(s -> s.id().equals("test-scaffold"))
                .anyMatch(s -> s.evidence().stream().anyMatch(e -> e.startsWith("war-hotpath:"))));
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

    private com.upgrd.core.model.SecurityReport emptySecurity() {
        return new com.upgrd.core.model.SecurityReport(
                "test", Instant.now(), ProjectProfile.UNKNOWN, List.of(), 0, 0);
    }
}
