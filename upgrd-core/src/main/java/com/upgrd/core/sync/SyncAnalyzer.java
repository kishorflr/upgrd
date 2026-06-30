package com.upgrd.core.sync;

import com.upgrd.core.model.SyncReport;
import com.upgrd.core.model.SyncSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SyncAnalyzer {

    public SyncReport compare(Set<String> warClasses, Set<String> sourceClasses) {
        return compare(warClasses, sourceClasses, Set.of(), Set.of());
    }

    public SyncReport compare(
            Set<String> warClasses,
            Set<String> sourceClasses,
            Set<String> warLibs,
            Set<String> sourceLibs) {
        List<String> onlyInWar = new ArrayList<>();
        List<String> onlyInSource = new ArrayList<>();
        List<String> inBoth = new ArrayList<>();

        for (String className : warClasses) {
            if (sourceClasses.contains(className)) {
                inBoth.add(className);
            } else {
                onlyInWar.add(className);
            }
        }
        for (String className : sourceClasses) {
            if (!warClasses.contains(className)) {
                onlyInSource.add(className);
            }
        }

        List<String> onlyInWarLibs = new ArrayList<>();
        List<String> onlyInSourceLibs = new ArrayList<>();
        List<String> inBothLibs = new ArrayList<>();
        for (String jar : warLibs) {
            if (sourceLibs.contains(jar)) {
                inBothLibs.add(jar);
            } else {
                onlyInWarLibs.add(jar);
            }
        }
        for (String jar : sourceLibs) {
            if (!warLibs.contains(jar)) {
                onlyInSourceLibs.add(jar);
            }
        }

        SyncSeverity severity = classifySeverity(
                warClasses.size(),
                onlyInWar.size(),
                onlyInSource.size(),
                onlyInWarLibs.size(),
                onlyInSourceLibs.size());

        return new SyncReport(
                warClasses.size(),
                sourceClasses.size(),
                List.copyOf(onlyInWar),
                List.copyOf(onlyInSource),
                List.copyOf(inBoth),
                warLibs.size(),
                sourceLibs.size(),
                List.copyOf(onlyInWarLibs),
                List.copyOf(onlyInSourceLibs),
                List.copyOf(inBothLibs),
                severity,
                severityReason(severity, onlyInWar, onlyInSource, onlyInWarLibs, onlyInSourceLibs));
    }

    private SyncSeverity classifySeverity(
            int warClassCount,
            int onlyInWar,
            int onlyInSource,
            int onlyInWarLibs,
            int onlyInSourceLibs) {
        if (warClassCount == 0 && onlyInWarLibs == 0) {
            return SyncSeverity.NONE;
        }
        if (onlyInWar == 0 && onlyInSource == 0 && onlyInWarLibs == 0 && onlyInSourceLibs == 0) {
            return SyncSeverity.IN_SYNC;
        }

        double warDriftRatio = warClassCount == 0 ? 0 : (double) onlyInWar / warClassCount;

        if (onlyInWar >= 10 || warDriftRatio >= 0.5 || onlyInWarLibs >= 5) {
            return SyncSeverity.CRITICAL;
        }
        if (onlyInWar >= 3 || warDriftRatio >= 0.25 || onlyInWarLibs >= 2) {
            return SyncSeverity.HIGH;
        }
        if (onlyInWar > 0 || onlyInSource > 0 || onlyInWarLibs > 0 || onlyInSourceLibs > 0) {
            return SyncSeverity.MEDIUM;
        }
        return SyncSeverity.LOW;
    }

    private String severityReason(
            SyncSeverity severity,
            List<String> onlyInWar,
            List<String> onlyInSource,
            List<String> onlyInWarLibs,
            List<String> onlyInSourceLibs) {
        return switch (severity) {
            case NONE -> "No WAR provided";
            case IN_SYNC -> "WAR and source classes/libs match";
            case LOW -> "Minor drift — review before apply";
            case MEDIUM -> String.format(
                    "%d production-only class(es), %d source-only, %d WAR-only lib(s)",
                    onlyInWar.size(), onlyInSource.size(), onlyInWarLibs.size());
            case HIGH, CRITICAL -> String.format(
                    "Production WAR is authoritative — %d class(es) only in WAR, %d lib(s) only in WAR; "
                            + "source is out of sync",
                    onlyInWar.size(), onlyInWarLibs.size());
        };
    }
}
