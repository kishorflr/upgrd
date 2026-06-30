package com.upgrd.core.model;

import java.util.List;

public record SyncReport(
        int warClassCount,
        int sourceClassCount,
        List<String> onlyInWar,
        List<String> onlyInSource,
        List<String> inBoth,
        int warLibCount,
        int sourceLibCount,
        List<String> onlyInWarLibs,
        List<String> onlyInSourceLibs,
        List<String> inBothLibs,
        SyncSeverity severity,
        String severityReason) {

    public static SyncReport empty() {
        return new SyncReport(
                0, 0, List.of(), List.of(), List.of(),
                0, 0, List.of(), List.of(), List.of(),
                SyncSeverity.NONE, "No WAR provided");
    }
}
