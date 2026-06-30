package com.upgrd.recipes.framework;

import com.upgrd.recipes.FileRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates Spring MVC migration hints for Struts JSP tags and validation.xml rules.
 */
public final class StrutsViewValidationHintsRecipe implements FileRecipe {

    private static final Pattern STRUTS_TAG = Pattern.compile(
            "(html:|bean:|logic:)[a-zA-Z]+");
    private static final Pattern FORM_SET = Pattern.compile(
            "<form-validation>[\\s\\S]*?</form-validation>", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORM_NAME = Pattern.compile(
            "<form\\s+name=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD = Pattern.compile(
            "<field\\s+property=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</field>", Pattern.CASE_INSENSITIVE);
    private static final Pattern VAR_NAME = Pattern.compile(
            "<var-name>([^<]+)</var-name>");
    private static final Pattern VAR_VALUE = Pattern.compile(
            "<var-value>([^<]+)</var-value>");

    @Override
    public String coordinate() {
        return "upgrd:StrutsViewToSpringHints";
    }

    @Override
    public String displayName() {
        return "Generate Struts JSP and validation.xml → Spring MVC hints";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (relativePath.endsWith(".jsp") && STRUTS_TAG.matcher(content).find()) {
            return Optional.of(new FileChange(hintsPath(relativePath), "", jspHints(relativePath, content)));
        }
        if (relativePath.contains("validation") && relativePath.endsWith(".xml")
                && content.contains("<form-validation")) {
            return Optional.of(new FileChange(hintsPath(relativePath), "", validationHints(relativePath, content)));
        }
        return Optional.empty();
    }

    private String hintsPath(String relativePath) {
        if (relativePath.endsWith(".jsp")) {
            return relativePath + ".struts-view-hints.md";
        }
        return relativePath.replace(".xml", ".struts-view-hints.md");
    }

    private String jspHints(String relativePath, String content) {
        List<String> tags = new ArrayList<>();
        Matcher tagMatcher = STRUTS_TAG.matcher(content);
        while (tagMatcher.find()) {
            String tag = tagMatcher.group();
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }

        String tagList = tags.isEmpty() ? "- (none)" : String.join("\n", tags.stream().map(t -> "- `" + t + "`").toList());
        return """
                # Struts JSP → Spring MVC hints (UpGrd)

                File: `%s`

                ## Detected Struts tag prefixes

                %s

                ## Spring equivalents

                | Struts | Spring MVC / Thymeleaf |
                |--------|-------------------------|
                | `html:form` | `<form>` + `@ModelAttribute` / `@Valid` |
                | `html:text` | `<input>` or `th:field="*{field}"` |
                | `bean:write` | `${model.field}` or `th:text` |
                | `logic:iterate` | `th:each` or JSTL `c:forEach` |

                ## Next steps

                1. Replace Struts form beans with POJO command objects (`@ModelAttribute`).
                2. Move validation from `validation.xml` to Bean Validation (`@NotNull`, `@Size`, …).
                3. Register a `ViewResolver` for JSP or migrate views to Thymeleaf incrementally.
                """.formatted(relativePath, tagList);
    }

    private String validationHints(String relativePath, String content) {
        List<String> forms = new ArrayList<>();
        Matcher formMatcher = FORM_NAME.matcher(content);
        while (formMatcher.find()) {
            forms.add("- Form bean: `" + formMatcher.group(1) + "`");
        }
        List<String> fields = new ArrayList<>();
        Matcher fieldMatcher = FIELD.matcher(content);
        while (fieldMatcher.find()) {
            fields.add("- Field `" + fieldMatcher.group(1) + "` → " + extractRule(fieldMatcher.group(2)));
        }

        return """
                # Struts validation.xml → Spring hints (UpGrd)

                File: `%s`

                ## Form rules

                %s

                %s

                ## Spring mapping

                ```java
                public class UserForm {
                    @NotBlank
                    private String username;
                    // mirror validation.xml rules with jakarta.validation annotations
                }

                @PostMapping("/user")
                public String submit(@Valid @ModelAttribute("userForm") UserForm form, BindingResult errors) {
                    if (errors.hasErrors()) {
                        return "login";
                    }
                    // ...
                }
                ```
                """.formatted(
                relativePath,
                forms.isEmpty() ? "_No forms parsed._" : String.join("\n", forms),
                fields.isEmpty() ? "_No field rules parsed._" : String.join("\n", fields));
    }

    private String extractRule(String fieldBody) {
        Matcher name = VAR_NAME.matcher(fieldBody);
        Matcher value = VAR_VALUE.matcher(fieldBody);
        if (name.find() && value.find()) {
            return "`@" + springAnnotation(name.group(1).trim()) + "` (was " + name.group(1) + "=" + value.group(1).trim() + ")";
        }
        return "review validation rule";
    }

    private String springAnnotation(String strutsRule) {
        return switch (strutsRule.toLowerCase()) {
            case "required" -> "NotBlank";
            case "minlength" -> "Size(min=…)";
            case "maxlength" -> "Size(max=…)";
            case "email" -> "Email";
            default -> "Valid (custom)";
        };
    }
}
