package com.upgrd.recipes.language;

import com.upgrd.recipes.FileRecipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mechanical Java 7→21 source upgrades safe for automated apply.
 */
public final class UpgradeJavaRecipe implements FileRecipe {

    private static final Map<Pattern, String> REPLACEMENTS = linkedReplacements();

    private static Map<Pattern, String> linkedReplacements() {
        Map<Pattern, String> map = new LinkedHashMap<>();
        map.put(Pattern.compile("\\bnew Integer\\("), "Integer.valueOf(");
        map.put(Pattern.compile("\\bnew Long\\("), "Long.valueOf(");
        map.put(Pattern.compile("\\bnew Boolean\\("), "Boolean.valueOf(");
        map.put(Pattern.compile("\\bnew Double\\("), "Double.valueOf(");
        map.put(Pattern.compile("\\bnew Float\\("), "Float.valueOf(");
        map.put(Pattern.compile("\\bnew Byte\\("), "Byte.valueOf(");
        map.put(Pattern.compile("\\bnew Short\\("), "Short.valueOf(");
        map.put(Pattern.compile("\\bnew Character\\("), "Character.valueOf(");
        map.put(Pattern.compile("\\bVector<"), "ArrayList<");
        map.put(Pattern.compile("\\bVector\\b"), "ArrayList");
        map.put(Pattern.compile("\\bHashtable<"), "HashMap<");
        map.put(Pattern.compile("\\bHashtable\\b"), "HashMap");
        return map;
    }

    @Override
    public String coordinate() {
        return "upgrd:UpgradeToJava21";
    }

    @Override
    public String displayName() {
        return "Upgrade Java source to Java 21 baseline";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }

        String after = content;
        boolean touchedVector = after.contains("Vector");
        boolean touchedHashtable = after.contains("Hashtable");

        for (var entry : REPLACEMENTS.entrySet()) {
            after = entry.getKey().matcher(after).replaceAll(entry.getValue());
        }

        if (touchedVector && !after.contains("import java.util.ArrayList")
                && !after.contains("import java.util.*")) {
            after = insertImport(after, "import java.util.ArrayList;");
        }
        if (touchedHashtable && !after.contains("import java.util.HashMap")
                && !after.contains("import java.util.*")) {
            after = insertImport(after, "import java.util.HashMap;");
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
