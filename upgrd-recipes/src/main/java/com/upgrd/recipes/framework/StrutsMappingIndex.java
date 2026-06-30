package com.upgrd.recipes.framework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Index of Struts 1 action mappings parsed from {@code struts-config.xml}.
 */
public final class StrutsMappingIndex {

    private static final Pattern ACTION = Pattern.compile(
            "<action\\s+([^>]+)>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("(\\w+)=\"([^\"]+)\"");

    private final Map<String, ActionMapping> byActionClass = new HashMap<>();
    private final Map<String, ActionMapping> bySimpleClassName = new HashMap<>();

    public static StrutsMappingIndex empty() {
        return new StrutsMappingIndex(Map.of());
    }

    public static StrutsMappingIndex loadFromProject(Path projectRoot) throws IOException {
        Map<String, ActionMapping> mappings = new HashMap<>();
        if (!Files.isDirectory(projectRoot)) {
            return new StrutsMappingIndex(mappings);
        }
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("struts-config.xml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            parse(content).forEach((type, mapping) -> mappings.put(type, mapping));
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to read " + path, ex);
                        }
                    });
        }
        return new StrutsMappingIndex(mappings);
    }

    static Map<String, ActionMapping> parse(String content) {
        Map<String, ActionMapping> result = new HashMap<>();
        Matcher actionMatcher = ACTION.matcher(content);
        while (actionMatcher.find()) {
            Map<String, String> attrs = parseAttributes(actionMatcher.group(1));
            String type = attrs.get("type");
            String path = attrs.get("path");
            if (type == null || path == null) {
                continue;
            }
            String formName = attrs.get("name");
            String scope = attrs.getOrDefault("scope", "request");
            result.put(type, new ActionMapping(path, formName, scope));
        }
        return result;
    }

    private static Map<String, String> parseAttributes(String attrBlock) {
        Map<String, String> attrs = new HashMap<>();
        Matcher matcher = ATTR.matcher(attrBlock);
        while (matcher.find()) {
            attrs.put(matcher.group(1).toLowerCase(), matcher.group(2));
        }
        return attrs;
    }

    StrutsMappingIndex(Map<String, ActionMapping> mappings) {
        mappings.forEach((type, mapping) -> {
            byActionClass.put(type, mapping);
            int dot = type.lastIndexOf('.');
            String simple = dot >= 0 ? type.substring(dot + 1) : type;
            bySimpleClassName.put(simple, mapping);
        });
    }

    public Optional<ActionMapping> findForClass(String className, String packageName) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        if (packageName != null && !packageName.isBlank()) {
            Optional<ActionMapping> fq = Optional.ofNullable(byActionClass.get(packageName + "." + className));
            if (fq.isPresent()) {
                return fq;
            }
        }
        return Optional.ofNullable(bySimpleClassName.get(className));
    }

    public record ActionMapping(String path, String formName, String scope) {

        public boolean hasForm() {
            return formName != null && !formName.isBlank();
        }
    }
}
