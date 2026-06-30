package com.upgrd.core.model;

import java.util.List;

public record UsageReport(
        int logFileCount,
        int totalHits,
        List<UsageHit> hits,
        List<String> unusedInWar) {
}
