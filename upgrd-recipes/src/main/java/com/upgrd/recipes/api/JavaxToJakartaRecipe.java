package com.upgrd.recipes.api;

import com.upgrd.recipes.FileRecipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rewrites {@code javax.*} EE imports to {@code jakarta.*} in Java sources.
 */
public final class JavaxToJakartaRecipe implements FileRecipe {

    private static final Map<String, String> PACKAGE_MAP = linkedMap();

    private static Map<String, String> linkedMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("javax.servlet", "jakarta.servlet");
        map.put("javax.persistence", "jakarta.persistence");
        map.put("javax.validation", "jakarta.validation");
        map.put("javax.annotation", "jakarta.annotation");
        map.put("javax.ejb", "jakarta.ejb");
        map.put("javax.transaction", "jakarta.transaction");
        map.put("javax.ws.rs", "jakarta.ws.rs");
        map.put("javax.xml.bind", "jakarta.xml.bind");
        map.put("javax.inject", "jakarta.inject");
        return map;
    }

    @Override
    public String coordinate() {
        return "upgrd:JavaxToJakarta";
    }

    @Override
    public String displayName() {
        return "Replace javax.* imports with jakarta.*";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java") && !relativePath.endsWith(".xml")) {
            return Optional.empty();
        }
        if (!content.contains("javax.")) {
            return Optional.empty();
        }

        String after = content;
        for (var entry : PACKAGE_MAP.entrySet()) {
            after = after.replace(entry.getKey(), entry.getValue());
        }

        if (after.equals(content)) {
            return Optional.empty();
        }
        return Optional.of(new FileChange(relativePath, content, after));
    }
}
