package com.upgrd.recipes.framework;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Index of Struts 1 action mappings, form beans, and forwards parsed from {@code struts-config.xml}.
 */
public final class StrutsMappingIndex {

    private static final Pattern ACTION_BLOCK = Pattern.compile(
            "<action\\s+([^>]+)>(.*?)</action>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FORM_BEAN = Pattern.compile(
            "<form-bean\\s+([^>]+)/?>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORWARD = Pattern.compile(
            "<forward\\s+name=\"([^\"]+)\"\\s+path=\"([^\"]+)\"\\s*/>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("(\\w+)=\"([^\"]+)\"");

    private final Map<String, ActionMapping> byActionClass = new HashMap<>();
    private final Map<String, ActionMapping> bySimpleClassName = new HashMap<>();
    private final Map<String, FormBean> formBeansByName = new HashMap<>();

    public static StrutsMappingIndex empty() {
        return new StrutsMappingIndex(Map.of(), Map.of());
    }

    public static StrutsMappingIndex loadFromProject(Path projectRoot) throws IOException {
        Map<String, ActionMapping> mappings = new LinkedHashMap<>();
        Map<String, FormBean> formBeans = new LinkedHashMap<>();
        if (!Files.isDirectory(projectRoot)) {
            return new StrutsMappingIndex(mappings, formBeans);
        }
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("struts-config.xml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            parseFormBeans(content).forEach((name, bean) -> formBeans.put(name, bean));
                            parseActions(content, formBeans).forEach((type, mapping) -> mappings.put(type, mapping));
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to read " + path, ex);
                        }
                    });
        }
        return new StrutsMappingIndex(mappings, formBeans);
    }

    static Map<String, FormBean> parseFormBeans(String content) {
        Map<String, FormBean> result = new LinkedHashMap<>();
        Matcher matcher = FORM_BEAN.matcher(content);
        while (matcher.find()) {
            Map<String, String> attrs = parseAttributes(matcher.group(1));
            String name = attrs.get("name");
            String type = attrs.get("type");
            if (name != null && type != null) {
                result.put(name, new FormBean(name, type));
            }
        }
        return result;
    }

    static Map<String, ActionMapping> parseActions(String content, Map<String, FormBean> formBeans) {
        Map<String, ActionMapping> result = new LinkedHashMap<>();
        Matcher actionMatcher = ACTION_BLOCK.matcher(content);
        while (actionMatcher.find()) {
            putAction(result, actionMatcher.group(1), actionMatcher.group(2), formBeans);
        }
        Pattern selfClosing = Pattern.compile("<action\\s+([^>]+)/\\s*>", Pattern.CASE_INSENSITIVE);
        Matcher selfMatcher = selfClosing.matcher(content);
        while (selfMatcher.find()) {
            putAction(result, selfMatcher.group(1), "", formBeans);
        }
        return result;
    }

    private static void putAction(
            Map<String, ActionMapping> result,
            String attrBlock,
            String actionBody,
            Map<String, FormBean> formBeans) {
        Map<String, String> attrs = parseAttributes(attrBlock);
        String type = attrs.get("type");
        String path = attrs.get("path");
        if (type == null || path == null) {
            return;
        }
        String formName = attrs.get("name");
        String scope = attrs.getOrDefault("scope", "request");
        Map<String, String> forwards = parseForwards(actionBody);
        String formBeanType = resolveFormBeanType(formName, formBeans);
        result.put(type, new ActionMapping(path, formName, scope, forwards, formBeanType));
    }

    static Map<String, ActionMapping> parse(String content) {
        Map<String, FormBean> formBeans = parseFormBeans(content);
        return parseActions(content, formBeans);
    }

    private static Map<String, String> parseForwards(String actionBody) {
        Map<String, String> forwards = new LinkedHashMap<>();
        Matcher forward = FORWARD.matcher(actionBody);
        while (forward.find()) {
            forwards.put(forward.group(1), forward.group(2));
        }
        return forwards;
    }

    private static String resolveFormBeanType(String formName, Map<String, FormBean> formBeans) {
        if (formName == null || formName.isBlank()) {
            return null;
        }
        FormBean bean = formBeans.get(formName);
        if (bean == null) {
            return inferFormClassName(formName);
        }
        if (bean.type().contains("DynaActionForm")) {
            return inferFormClassName(formName);
        }
        return bean.type();
    }

    static String inferFormClassName(String formName) {
        if (formName == null || formName.isBlank()) {
            return "Object";
        }
        String base = formName;
        if (base.endsWith("Form")) {
            base = base.substring(0, base.length() - 4);
        }
        if (base.isEmpty()) {
            return capitalize(formName);
        }
        return capitalize(base) + "Form";
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Map<String, String> parseAttributes(String attrBlock) {
        Map<String, String> attrs = new HashMap<>();
        Matcher matcher = ATTR.matcher(attrBlock);
        while (matcher.find()) {
            attrs.put(matcher.group(1).toLowerCase(), matcher.group(2));
        }
        return attrs;
    }

    StrutsMappingIndex(Map<String, ActionMapping> mappings, Map<String, FormBean> formBeans) {
        mappings.forEach((type, mapping) -> {
            byActionClass.put(type, mapping);
            int dot = type.lastIndexOf('.');
            String simple = dot >= 0 ? type.substring(dot + 1) : type;
            bySimpleClassName.put(simple, mapping);
        });
        formBeansByName.putAll(formBeans);
    }

    public Map<String, FormBean> formBeans() {
        return Map.copyOf(formBeansByName);
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

    /**
     * Maps a Struts forward JSP path to a Thymeleaf logical view name.
     */
    public static String jspPathToViewName(String forwardPath) {
        if (forwardPath == null || forwardPath.isBlank()) {
            return forwardPath;
        }
        String view = forwardPath.trim();
        if (view.startsWith("/")) {
            view = view.substring(1);
        }
        if (view.endsWith(".jsp")) {
            view = view.substring(0, view.length() - 4);
        }
        return view;
    }

    public record FormBean(String name, String type) {
    }

    public record ActionMapping(
            String path,
            String formName,
            String scope,
            Map<String, String> forwards,
            String formBeanType) {

        public ActionMapping(String path, String formName, String scope) {
            this(path, formName, scope, Map.of(), null);
        }

        public boolean hasForm() {
            return formName != null && !formName.isBlank();
        }

        public String resolveView(String forwardName) {
            String path = forwards.getOrDefault(forwardName, forwardName);
            return jspPathToViewName(path);
        }

        public String simpleFormType() {
            if (formBeanType == null || formBeanType.isBlank()) {
                return inferFormClassName(formName);
            }
            int dot = formBeanType.lastIndexOf('.');
            return dot >= 0 ? formBeanType.substring(dot + 1) : formBeanType;
        }

        public String formBeanFqcn(String defaultPackage) {
            if (formBeanType != null && formBeanType.contains(".")) {
                return formBeanType;
            }
            String simple = formBeanType != null && !formBeanType.isBlank()
                    ? formBeanType
                    : inferFormClassName(formName);
            return defaultPackage + "." + simple;
        }
    }
}
