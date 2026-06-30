package com.upgrd.core.sync;

import com.upgrd.core.model.SyncSeverity;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncAnalyzerTest {

    private final SyncAnalyzer analyzer = new SyncAnalyzer();

    @Test
    void comparesWarAndSourceClasses() {
        var report = analyzer.compare(
                Set.of("com.example.A", "com.example.B"),
                Set.of("com.example.A", "com.example.C"));

        assertEquals(2, report.warClassCount());
        assertEquals(2, report.sourceClassCount());
        assertEquals(1, report.inBoth().size());
        assertEquals("com.example.B", report.onlyInWar().getFirst());
        assertEquals("com.example.C", report.onlyInSource().getFirst());
        assertEquals(SyncSeverity.CRITICAL, report.severity());
    }

    @Test
    void comparesWarAndSourceLibs() {
        var report = analyzer.compare(
                Set.of("com.example.A"),
                Set.of("com.example.A"),
                Set.of("log4j-1.2.17.jar", "spring-core.jar"),
                Set.of("spring-core.jar", "commons-lang.jar"));

        assertEquals(1, report.onlyInWarLibs().size());
        assertEquals("log4j-1.2.17.jar", report.onlyInWarLibs().getFirst());
        assertEquals(1, report.onlyInSourceLibs().size());
        assertEquals("commons-lang.jar", report.onlyInSourceLibs().getFirst());
    }

    @Test
    void criticalWhenManyWarOnlyClasses() {
        var report = analyzer.compare(
                Set.of(
                        "com.a.A", "com.a.B", "com.a.C", "com.a.D", "com.a.E",
                        "com.a.F", "com.a.G", "com.a.H", "com.a.I", "com.a.J"),
                Set.of("com.a.A"));

        assertEquals(SyncSeverity.CRITICAL, report.severity());
        assertTrue(report.severityReason().contains("WAR"));
    }

    @Test
    void inSyncWhenMatching() {
        var report = analyzer.compare(
                Set.of("com.example.A"),
                Set.of("com.example.A"),
                Set.of("app.jar"),
                Set.of("app.jar"));

        assertEquals(SyncSeverity.IN_SYNC, report.severity());
    }
}
