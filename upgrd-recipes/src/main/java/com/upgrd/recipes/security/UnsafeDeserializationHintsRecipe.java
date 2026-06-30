package com.upgrd.recipes.security;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documents unsafe Java deserialization hotspots (advisory).
 */
public final class UnsafeDeserializationHintsRecipe implements FileRecipe {

    private static final Pattern DESERIAL = Pattern.compile(
            "(ObjectInputStream\\s*\\([^)]+\\)[^;]*;)",
            Pattern.DOTALL);

    @Override
    public String coordinate() {
        return "upgrd:RemediateDeserialization";
    }

    @Override
    public String displayName() {
        return "Document unsafe deserialization for manual refactor";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        Matcher matcher = DESERIAL.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String snippet = matcher.group(1).trim();
        String hintsPath = relativePath.replace(".java", ".deserialization-refactor-hints.md");
        String hints = """
                # Deserialization refactor hints (UpGrd)

                File: `%s`

                ## Detected pattern

                ```java
                %s
                ```

                ## Recommendation

                Avoid Java native serialization for untrusted data (CWE-502). Prefer JSON/XML with schema validation,
                or an allow-listed deserializer with type filtering.

                ```java
                // Example: validate type before reading
                // ObjectInputFilter filter = ObjectInputFilter.Config.createFilter("com.example.AllowedType;!*");
                ```
                """.formatted(relativePath, snippet);

        return Optional.of(new FileChange(hintsPath, "", hints));
    }
}
