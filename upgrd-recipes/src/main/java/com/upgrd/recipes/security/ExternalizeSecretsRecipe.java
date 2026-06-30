package com.upgrd.recipes.security;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces hardcoded secrets in properties files with environment variable placeholders.
 */
public final class ExternalizeSecretsRecipe implements FileRecipe {

    private static final Pattern SECRET_LINE = Pattern.compile(
            "(?m)^(?<key>[A-Za-z0-9._-]*(password|secret|api[_-]?key|token)[A-Za-z0-9._-]*)\\s*=\\s*(?<value>[^\\s#$\\{].*)\\s*$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String coordinate() {
        return "upgrd:ExternalizeSecrets";
    }

    @Override
    public String displayName() {
        return "Externalize hardcoded credentials to environment variables";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".properties")) {
            return Optional.empty();
        }
        Matcher matcher = SECRET_LINE.matcher(content);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String envKey = matcher.group("key").toUpperCase().replace('.', '_');
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                    matcher.group("key") + "=${" + envKey + "}"));
            changed = true;
        }
        if (!changed) {
            return Optional.empty();
        }
        matcher.appendTail(sb);
        return Optional.of(new FileChange(relativePath, content, sb.toString()));
    }
}
