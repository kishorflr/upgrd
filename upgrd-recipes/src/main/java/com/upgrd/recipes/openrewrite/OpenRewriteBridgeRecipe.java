package com.upgrd.recipes.openrewrite;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;

/**
 * Placeholder for OpenRewrite-backed recipes. Coordinates starting with {@code org.openrewrite}
 * will delegate here until the OpenRewrite Maven plugin is wired in M4+.
 */
public final class OpenRewriteBridgeRecipe implements FileRecipe {

    private final String coordinate;
    private final String displayName;

    public OpenRewriteBridgeRecipe(String coordinate, String displayName) {
        this.coordinate = coordinate;
        this.displayName = displayName;
    }

    @Override
    public String coordinate() {
        return coordinate;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        return Optional.empty();
    }
}
