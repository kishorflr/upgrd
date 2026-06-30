package com.upgrd.core.sync;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }
}
