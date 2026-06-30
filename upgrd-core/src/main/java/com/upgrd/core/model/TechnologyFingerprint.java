package com.upgrd.core.model;

import java.util.List;

public record TechnologyFingerprint(
        List<String> frameworks,
        LoggingFramework logging,
        ServletApi servletApi,
        String persistenceHint,
        List<String> riskSignals,
        List<String> evidence) {
}
