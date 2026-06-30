package com.upgrd.core.model;

import java.nio.file.Path;

public record WarApplyOptions(
        Path warFile,
        SyncReport syncReport,
        WarConflictPolicy conflictPolicy) {

    public static WarApplyOptions disabled() {
        return new WarApplyOptions(null, null, WarConflictPolicy.WAR_WINS);
    }

    public boolean enabled() {
        return warFile != null && syncReport != null;
    }
}
