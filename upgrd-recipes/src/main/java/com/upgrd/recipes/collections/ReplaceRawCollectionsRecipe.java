package com.upgrd.recipes.collections;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Replaces raw collection types with parameterized equivalents where safe.
 */
public final class ReplaceRawCollectionsRecipe implements FileRecipe {

    private static final Pattern RAW_LIST = Pattern.compile("\\bList\\s+(\\w+)\\s*=\\s*new\\s+ArrayList\\s*\\(\\s*\\)");
    private static final Pattern RAW_MAP = Pattern.compile("\\bMap\\s+(\\w+)\\s*=\\s*new\\s+HashMap\\s*\\(\\s*\\)");

    @Override
    public String coordinate() {
        return "upgrd:ReplaceRawCollections";
    }

    @Override
    public String displayName() {
        return "Replace raw collection declarations";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        if (!content.contains("List ") && !content.contains("Map ")) {
            return Optional.empty();
        }

        String after = content;
        after = RAW_LIST.matcher(after).replaceAll("List<Object> $1 = new ArrayList<>()");
        after = RAW_MAP.matcher(after).replaceAll("Map<String, Object> $1 = new HashMap<>()");

        if (after.contains("ArrayList<>") && !after.contains("import java.util.ArrayList")
                && !after.contains("import java.util.*")) {
            after = insertImport(after, "import java.util.ArrayList;");
        }
        if (after.contains("HashMap<>") && !after.contains("import java.util.HashMap")
                && !after.contains("import java.util.*")) {
            after = insertImport(after, "import java.util.HashMap;");
        }
        if (after.contains("List<Object>") && !after.contains("import java.util.List")
                && !after.contains("import java.util.*")) {
            after = insertImport(after, "import java.util.List;");
        }
        if (after.contains("Map<String, Object>") && !after.contains("import java.util.Map")
                && !after.contains("import java.util.*")) {
            after = insertImport(after, "import java.util.Map;");
        }

        if (after.equals(content)) {
            return Optional.empty();
        }
        return Optional.of(new FileChange(relativePath, content, after));
    }

    private String insertImport(String content, String importLine) {
        if (content.contains(importLine)) {
            return content;
        }
        int packageIdx = content.indexOf("package ");
        if (packageIdx >= 0) {
            int semi = content.indexOf(';', packageIdx);
            return content.substring(0, semi + 1) + "\n\n" + importLine + "\n" + content.substring(semi + 1);
        }
        return importLine + "\n" + content;
    }
}
