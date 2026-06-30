package com.upgrd.core.sync;

import com.upgrd.core.model.SyncReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SyncAnalyzer {

    public SyncReport compare(Set<String> warClasses, Set<String> sourceClasses) {
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

        return new SyncReport(
                warClasses.size(),
                sourceClasses.size(),
                onlyInWar,
                onlyInSource,
                inBoth);
    }
}
