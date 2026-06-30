package com.upgrd.recipes.security;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documents SQL concatenation hotspots and suggests PreparedStatement refactors (advisory).
 */
public final class SqlConcatenationHintsRecipe implements FileRecipe {

    private static final Pattern SQL_CONCAT = Pattern.compile(
            "(\"\\s*SELECT[^\"]+\"\\s*\\+[^;]+;)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String coordinate() {
        return "upgrd:RemediateSqlConcatenation";
    }

    @Override
    public String displayName() {
        return "Document SQL concatenation for manual PreparedStatement refactor";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        Matcher matcher = SQL_CONCAT.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String snippet = matcher.group(1).trim();
        String hintsPath = relativePath.replace(".java", ".sql-refactor-hints.md");
        String hints = """
                # SQL refactor hints (UpGrd)

                File: `%s`

                ## Detected pattern

                ```java
                %s
                ```

                ## Recommendation

                Replace string concatenation with `PreparedStatement` and bound parameters to mitigate CWE-89.

                ```java
                // Example
                PreparedStatement ps = connection.prepareStatement("SELECT * FROM users WHERE id = ?");
                ps.setString(1, userId);
                ```
                """.formatted(relativePath, snippet);

        return Optional.of(new FileChange(hintsPath, "", hints));
    }
}
