package com.upgrd.recipes.security;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemediateWeakHashRecipe implements FileRecipe {

    private static final Pattern WEAK_HASH = Pattern.compile(
            "MessageDigest\\.getInstance\\(\"(MD5|SHA-1|SHA1)\"\\)");

    @Override
    public String coordinate() {
        return "upgrd:RemediateWeakHash";
    }

    @Override
    public String displayName() {
        return "Replace weak hash algorithms with SHA-256";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        Matcher matcher = WEAK_HASH.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String after = WEAK_HASH.matcher(content).replaceAll("MessageDigest.getInstance(\"SHA-256\")");
        return after.equals(content) ? Optional.empty() : Optional.of(new FileChange(relativePath, content, after));
    }
}
