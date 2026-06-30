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
    private static final Pattern FIELD = Pattern.compile(
            "property=\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private final Map<String, List<String>> fieldsByForm = new LinkedHashMap<>();

    public static StrutsValidationIndex empty() {
        return new StrutsValidationIndex(Map.of());
    }

    public static StrutsValidationIndex loadFromProject(Path projectRoot) throws IOException {
        Map<String, List<String>> fields = new LinkedHashMap<>();
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

    static Map<String, List<String>> parse(String content) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Matcher formMatcher = FORM.matcher(content);
        while (formMatcher.find()) {
            String formName = formMatcher.group(1);
            List<String> properties = new ArrayList<>();
            Matcher fieldMatcher = FIELD.matcher(formMatcher.group(2));
            while (fieldMatcher.find()) {
                properties.add(fieldMatcher.group(1));
            }
            if (!properties.isEmpty()) {
                result.put(formName, List.copyOf(properties));
            }
        }
        return result;
    }

    private StrutsValidationIndex(Map<String, List<String>> fieldsByForm) {
        this.fieldsByForm.putAll(fieldsByForm);
    }

    public List<String> fieldsFor(String formName) {
        return fieldsByForm.getOrDefault(formName, List.of());
    }
}
