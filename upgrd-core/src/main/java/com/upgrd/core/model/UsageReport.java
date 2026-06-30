package com.upgrd.core.model;

import java.util.List;
import java.util.Map;

public record UsageReport(
        int logFileCount,
        int totalHits,
        List<UsageHit> hits,
        List<String> unusedInWar,
        List<LogSourceEntry> logSources,
        Map<String, Integer> hitsByLogKind,
        List<BrokenAccessSignal> brokenSignals) {

    public UsageReport(int logFileCount, int totalHits, List<UsageHit> hits, List<String> unusedInWar) {
        this(logFileCount, totalHits, hits, unusedInWar, List.of(), Map.of(), List.of());
    }
}
