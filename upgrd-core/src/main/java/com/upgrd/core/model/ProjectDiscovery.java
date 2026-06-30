package com.upgrd.core.model;

import java.util.List;

public record ProjectDiscovery(
        BuildSystem buildSystem,
        String javaVersionHint,
        List<String> sourceRoots,
        List<String> webInfDescriptors,
        boolean containsWeblogicApi,
        TechnologyFingerprint fingerprint,
        ProjectProfile profile) {
}
