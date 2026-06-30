package com.upgrd.core.model;

import java.util.List;

public record UpgradePlan(
        String upgrdVersion,
        boolean dryRun,
        String targetJava,
        String productionServer,
        String localServer,
        List<UpgradeStep> steps) {
}
