package com.upgrd.recipes.framework;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Migrates common Spring MVC 4.x patterns to Spring 6 / Jakarta-compatible equivalents.
 */
public final class Spring4To6Recipe implements FileRecipe {

    private static final Pattern CONFIG_ADAPTER = Pattern.compile(
            "extends\\s+WebMvcConfigurerAdapter\\b");
    private static final Pattern INTERCEPTOR_ADAPTER = Pattern.compile(
            "extends\\s+HandlerInterceptorAdapter\\b");

    @Override
    public String coordinate() {
        return "upgrd:Spring4To6";
    }

    @Override
    public String displayName() {
        return "Upgrade Spring MVC 4.x to Spring 6 baseline";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        if (!content.contains("org.springframework")) {
            return Optional.empty();
        }

        String after = content;
        after = after.replaceAll(
                "import org\\.springframework\\.web\\.servlet\\.config\\.annotation\\.WebMvcConfigurerAdapter;\\s*",
                "");
        after = after.replaceAll(
                "import org\\.springframework\\.web\\.servlet\\.handler\\.HandlerInterceptorAdapter;\\s*",
                "");
        after = CONFIG_ADAPTER.matcher(after).replaceAll("implements WebMvcConfigurer");
        after = INTERCEPTOR_ADAPTER.matcher(after).replaceAll("implements HandlerInterceptor");

        if (after.contains("implements WebMvcConfigurer")
                && !after.contains("import org.springframework.web.servlet.config.annotation.WebMvcConfigurer")) {
            after = ensureImport(after, "import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;");
        }
        if (after.contains("implements HandlerInterceptor")
                && !after.contains("import org.springframework.web.servlet.HandlerInterceptor")) {
            after = ensureImport(after, "import org.springframework.web.servlet.HandlerInterceptor;");
        }

        after = after.replaceAll(
                "@RequestMapping\\(\\s*value\\s*=\\s*\"([^\"]+)\"\\s*,\\s*method\\s*=\\s*RequestMethod\\.GET\\s*\\)",
                "@GetMapping(\"$1\")");
        after = after.replaceAll(
                "@RequestMapping\\(\\s*method\\s*=\\s*RequestMethod\\.GET\\s*,\\s*value\\s*=\\s*\"([^\"]+)\"\\s*\\)",
                "@GetMapping(\"$1\")");

        if (after.contains("@GetMapping") && !after.contains("import org.springframework.web.bind.annotation.GetMapping")) {
            after = ensureImport(after, "import org.springframework.web.bind.annotation.GetMapping;");
        }

        if (after.equals(content)) {
            return Optional.empty();
        }
        return Optional.of(new FileChange(relativePath, content, after));
    }

    private String ensureImport(String content, String importLine) {
        if (content.contains(importLine)) {
            return content;
        }
        int packageIdx = content.indexOf("package ");
        if (packageIdx >= 0) {
            int semi = content.indexOf(';', packageIdx);
            int insertAt = semi + 1;
            while (insertAt < content.length() && content.charAt(insertAt) != '\n') {
                insertAt++;
            }
            insertAt++;
            return content.substring(0, insertAt) + importLine + "\n" + content.substring(insertAt);
        }
        return importLine + "\n" + content;
    }
}
