package com.upgrd.recipes.framework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses Struts {@code validation.xml} field definitions for form bean scaffolding.
 */
public final class StrutsValidationIndex {

    private static final Pattern FORM = Pattern.compile(
            "<form\\s+name=\"([^\"]+)\">(.*?)</form>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FIELD_BLOCK = Pattern.compile(
            "<field\\s+property=\"([^\"]+)\"(?:\\s+depends=\"([^\"]+)\")?\\s*(?:/>|>(.*?)</field>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern VAR = Pattern.compile(
            "<var-name>\\s*([^<]+?)\\s*</var-name>\\s*<var-value>\\s*([^<]+?)\\s*</var-value>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final Map<String, List<ValidationField>> fieldsByForm = new LinkedHashMap<>();

    public static StrutsValidationIndex empty() {
        return new StrutsValidationIndex(Map.of());
    }

    public static StrutsValidationIndex loadFromProject(Path projectRoot) throws IOException {
        Map<String, List<ValidationField>> fields = new LinkedHashMap<>();
        if (!Files.isDirectory(projectRoot)) {
            return new StrutsValidationIndex(fields);
        }
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("validation.xml"))
                    .forEach(path -> {
                        try {
                            parse(Files.readString(path, StandardCharsets.UTF_8)).forEach(fields::putIfAbsent);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to read " + path, ex);
                        }
                    });
        }
        return new StrutsValidationIndex(fields);
    }

    static Map<String, List<ValidationField>> parse(String content) {
        Map<String, List<ValidationField>> result = new LinkedHashMap<>();
        Matcher formMatcher = FORM.matcher(content);
        while (formMatcher.find()) {
            String formName = formMatcher.group(1);
            List<ValidationField> properties = new ArrayList<>();
            Matcher fieldMatcher = FIELD_BLOCK.matcher(formMatcher.group(2));
            while (fieldMatcher.find()) {
                String property = fieldMatcher.group(1);
                String dependsRaw = fieldMatcher.group(2);
                String fieldBody = fieldMatcher.group(3);
                List<String> depends = dependsRaw == null || dependsRaw.isBlank()
                        ? List.of()
                        : List.of(dependsRaw.split("\\s*,\\s*"));
                Map<String, String> vars = parseVars(fieldBody);
                properties.add(new ValidationField(property, depends, vars));
            }
            if (!properties.isEmpty()) {
                result.put(formName, List.copyOf(properties));
            }
        }
        return result;
    }

    private static Map<String, String> parseVars(String fieldBody) {
        if (fieldBody == null || fieldBody.isBlank()) {
            return Map.of();
        }
        Map<String, String> vars = new LinkedHashMap<>();
        Matcher matcher = VAR.matcher(fieldBody);
        while (matcher.find()) {
            vars.put(matcher.group(1).trim().toLowerCase(), matcher.group(2).trim());
        }
        return vars;
    }

    private StrutsValidationIndex(Map<String, List<ValidationField>> fieldsByForm) {
        this.fieldsByForm.putAll(fieldsByForm);
    }

    public List<ValidationField> fieldsFor(String formName) {
        return fieldsByForm.getOrDefault(formName, List.of());
    }

    public List<String> propertyNamesFor(String formName) {
        return fieldsFor(formName).stream().map(ValidationField::property).toList();
    }

    public record ValidationField(String property, List<String> depends, Map<String, String> vars) {

        public ValidationField(String property, List<String> depends) {
            this(property, depends, Map.of());
        }

        public boolean hasRule(String rule) {
            return depends.stream().anyMatch(d -> d.equalsIgnoreCase(rule));
        }

        public Integer minLength() {
            return parseLength("minlength");
        }

        public Integer maxLength() {
            return parseLength("maxlength");
        }

        private Integer parseLength(String key) {
            String value = vars.get(key);
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        public boolean hasSizeConstraint() {
            return minLength() != null || maxLength() != null;
        }
    }
}
