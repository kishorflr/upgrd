package com.upgrd.recipes;

import java.util.Optional;

/**
 * Deterministic, auditable file transform executed during apply.
 * OpenRewrite-backed recipes will implement this interface as adapters in a follow-up.
 */
public interface FileRecipe {

    String coordinate();

    String displayName();

    Optional<FileChange> transform(String relativePath, String content);

    record FileChange(String relativePath, String before, String after) {
    }
}
