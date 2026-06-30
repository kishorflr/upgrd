package com.upgrd.recipes.framework;

import com.upgrd.recipes.ProjectAwareRecipe;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migrates Struts 1 {@code Action} classes to Spring MVC {@code @Controller} stubs,
 * preserving execute() body statements where possible.
 */
public final class StrutsActionToSpringControllerRecipe implements ProjectAwareRecipe {

    private static final Pattern PACKAGE = Pattern.compile("package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_ACTION = Pattern.compile(
            "public\\s+class\\s+(\\w+)\\s+extends\\s+Action\\b");
    private static final Pattern EXECUTE_BLOCK = Pattern.compile(
            "public\\s+ActionForward\\s+execute\\s*\\(\\s*ActionMapping\\s+\\w+\\s*,\\s*(\\w+)\\s+(\\w+)\\s*,"
                    + "\\s*HttpServletRequest\\s+\\w+\\s*,\\s*HttpServletResponse\\s+\\w+\\s*\\)\\s*throws\\s+Exception\\s*\\{"
                    + "([\\s\\S]*?)return\\s+mapping\\.findForward\\(\"(\\w+)\"\\)\\s*;\\s*\\}",
            Pattern.DOTALL);

    private StrutsMappingIndex index = StrutsMappingIndex.empty();

    @Override
    public void prepare(Path projectRoot) throws IOException {
        index = StrutsMappingIndex.loadFromProject(projectRoot);
    }

    @Override
    public String coordinate() {
        return "upgrd:StrutsActionToSpringController";
    }

    @Override
    public String displayName() {
        return "Migrate Struts 1 Action to Spring MVC Controller";
    }

    @Override
    public Optional<FileChange> transform(String relativePath, String content) {
        if (!relativePath.endsWith(".java")) {
            return Optional.empty();
        }
        if (!content.contains("org.apache.struts") && !content.contains("extends Action")) {
            return Optional.empty();
        }

        Matcher classMatcher = CLASS_ACTION.matcher(content);
        if (!classMatcher.find()) {
            return Optional.empty();
        }
        String className = classMatcher.group(1);
        String packageName = extractPackage(content).orElse("com.example");
        StrutsMappingIndex.ActionMapping mapping = index.findForClass(className, packageName)
                .orElse(fallbackMapping(className));

        String after = content;
        after = after.replaceAll("import org\\.apache\\.struts\\.action\\.\\*;\\s*", "");
        after = after.replaceAll("import org\\.apache\\.struts\\.action\\.\\w+;\\s*", "");
        after = after.replaceFirst("public\\s+class\\s+" + className + "\\s+extends\\s+Action\\b",
                "public class " + className);

        after = ensureImport(after, "import org.springframework.stereotype.Controller;");
        after = ensureImport(after, "import org.springframework.web.bind.annotation.GetMapping;");
        after = ensureImport(after, "import org.springframework.web.bind.annotation.PostMapping;");
        after = ensureImport(after, "import org.springframework.web.bind.annotation.ModelAttribute;");

        Matcher executeMatcher = EXECUTE_BLOCK.matcher(after);
        if (executeMatcher.find()) {
            String formType = executeMatcher.group(1);
            String formParam = executeMatcher.group(2);
            String body = executeMatcher.group(3).trim();
            String forward = executeMatcher.group(4);
            String viewName = mapping.resolveView(forward);
            String method = buildControllerMethod(mapping, packageName, formType, formParam, body, viewName);
            after = executeMatcher.replaceFirst(Matcher.quoteReplacement(method));
            after = addFormImportIfNeeded(after, mapping, packageName, formType);
        }

        if (!after.contains("@Controller")) {
            after = after.replaceFirst(
                    "public\\s+class\\s+" + className,
                    "@Controller\npublic class " + className);
        }

        if (after.equals(content)) {
            return Optional.empty();
        }
        return Optional.of(new FileChange(relativePath, content, after));
    }

    private StrutsMappingIndex.ActionMapping fallbackMapping(String className) {
        String path = "/" + className.replace("Action", "").toLowerCase();
        if (path.equals("/")) {
            path = "/home";
        }
        return new StrutsMappingIndex.ActionMapping(path, null, "request");
    }

    private String buildControllerMethod(
            StrutsMappingIndex.ActionMapping mapping,
            String packageName,
            String formType,
            String formParam,
            String body,
            String viewName) {
        String path = mapping.path();
        boolean hasForm = mapping.hasForm()
                || (formParam != null && !formParam.isBlank() && !"_".equals(formParam)
                && !"ActionForm".equals(formType));
        if (hasForm) {
            String formName = mapping.hasForm() ? mapping.formName() : formParam;
            String modelType = resolveModelType(mapping, packageName, formType);
            String param = "@ModelAttribute(\"" + formName + "\") " + modelType + " " + formParam;
            return """
                    @PostMapping("%s")
                    public String execute(%s, HttpServletRequest request, HttpServletResponse response) {
                        // UpGrd: migrated from Struts Action — review form binding for '%s'
                    %s
                        return "%s";
                    }
                    """.formatted(path, param, formName, indent(body, 8), viewName);
        }
        return """
                @GetMapping("%s")
                public String execute(HttpServletRequest request, HttpServletResponse response) {
                    // UpGrd: migrated from Struts Action
                %s
                    return "%s";
                }
                """.formatted(path, indent(body, 8), viewName);
    }

    private String resolveModelType(
            StrutsMappingIndex.ActionMapping mapping,
            String packageName,
            String executeFormType) {
        if (!"ActionForm".equals(executeFormType) && !executeFormType.isBlank()) {
            return executeFormType;
        }
        if (mapping.formBeanType() != null && mapping.formBeanType().contains(".")) {
            return mapping.simpleFormType();
        }
        return mapping.simpleFormType();
    }

    private String addFormImportIfNeeded(
            String content,
            StrutsMappingIndex.ActionMapping mapping,
            String packageName,
            String executeFormType) {
        if (!"ActionForm".equals(executeFormType) && executeFormType.contains(".")) {
            return ensureImport(content, "import " + executeFormType + ";");
        }
        String fqcn = mapping.formBeanFqcn(packageName);
        if (fqcn.startsWith(packageName + ".")) {
            return content;
        }
        return ensureImport(content, "import " + fqcn + ";");
    }

    private Optional<String> extractPackage(String content) {
        Matcher matcher = PACKAGE.matcher(content);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private String indent(String body, int spaces) {
        if (body.isBlank()) {
            return "";
        }
        String pad = " ".repeat(spaces);
        return body.lines().map(line -> pad + line).reduce((a, b) -> a + "\n" + b).orElse("");
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
            while (insertAt < content.length()) {
                int lineEnd = content.indexOf('\n', insertAt);
                String line = lineEnd < 0
                        ? content.substring(insertAt)
                        : content.substring(insertAt, lineEnd);
                String trimmed = line.trim();
                if (trimmed.startsWith("import ")) {
                    insertAt = lineEnd + 1;
                } else if (trimmed.isEmpty()) {
                    insertAt = lineEnd + 1;
                } else {
                    break;
                }
            }
            return content.substring(0, insertAt) + importLine + "\n" + content.substring(insertAt);
        }
        return importLine + "\n" + content;
    }
}
