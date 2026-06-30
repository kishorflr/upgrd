package com.upgrd.recipes.logging;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Migrates log4j 1.x {@code Logger.getLogger} usage to SLF4J {@code LoggerFactory.getLogger}.
 */
public final class Log4j1ToSlf4jRecipe implements FileRecipe {

    private static final Pattern LOG4J_IMPORT = Pattern.compile("import org\\.apache\\.log4j\\.Logger;\\s*");

    @Override
    public String coordinate() {
        return "upgrd:Log4j1ToSlf4j";
    }

    @Override
    public String displayName() {
        return "Migrate Log4j 1.x to SLF4J";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java") || !content.contains("org.apache.log4j")) {
            return Optional.empty();
        }

        String after = LOG4J_IMPORT.matcher(content).replaceFirst("""
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                """);
        after = after.replace("Logger.getLogger(", "LoggerFactory.getLogger(");

        if (after.equals(content)) {
            return Optional.empty();
        }
        return Optional.of(new FileChange(relativePath, content, after));
    }
}
