package com.upgrd.core.model;

import java.nio.file.Path;

public record WarContext(
        String warPath,
        WarConflictPolicy defaultPolicy) {
}
