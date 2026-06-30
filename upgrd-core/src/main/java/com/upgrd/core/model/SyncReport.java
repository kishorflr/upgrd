package com.upgrd.core.model;

import java.util.List;

public record SyncReport(
        int warClassCount,
        int sourceClassCount,
        List<String> onlyInWar,
        List<String> onlyInSource,
        List<String> inBoth) {
}
