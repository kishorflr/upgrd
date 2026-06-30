package com.upgrd.recipes.framework;

import com.upgrd.recipes.FileRecipe;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migrates Struts 1 {@code Action} classes to Spring MVC {@code @Controller} stubs.
 */
public final class StrutsActionToSpringControllerRecipe implements FileRecipe {

    private static final Pattern CLASS_ACTION = Pattern.compile(
            "public\\s+class\\s+(\\w+)\\s+extends\\s+Action\\b");
    private static final Pattern EXECUTE_METHOD = Pattern.compile(
            "public\\s+ActionForward\\s+execute\\s*\\(\\s*ActionMapping\\s+\\w+\\s*,\\s*ActionForm\\s+\\w+\\s*,"
                    + "\\s*HttpServletRequest\\s+\\w+\\s*,\\s*HttpServletResponse\\s+\\w+\\s*\\)\\s*throws\\s+Exception\\s*\\{"
                    + "[\\s\\S]*?return\\s+mapping\\.findForward\\(\"(\\w+)\"\\);\\s*\\}",
            Pattern.DOTALL);

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
        String mappingPath = "/" + className.replace("Action", "").toLowerCase();
        if (mappingPath.equals("/")) {
            mappingPath = "/home";
        }

        String after = content;
        after = after.replaceAll("import org\\.apache\\.struts\\.action\\.\\*;\\s*", "");
        after = after.replaceAll("import org\\.apache\\.struts\\.action\\.\\w+;\\s*", "");
        after = after.replaceFirst("public\\s+class\\s+" + className + "\\s+extends\\s+Action\\b",
                "public class " + className);

        after = ensureImport(after, "import org.springframework.stereotype.Controller;");
        after = ensureImport(after, "import org.springframework.web.bind.annotation.GetMapping;");

        Matcher executeMatcher = EXECUTE_METHOD.matcher(after);
        if (executeMatcher.find()) {
            String forward = executeMatcher.group(1);
            after = executeMatcher.replaceFirst("""
                    @GetMapping("%s")
                    public String execute(HttpServletRequest request, HttpServletResponse response) {
                        // UpGrd: migrated from Struts Action — review URL mapping
                        return "%s";
                    }
                    """.formatted(mappingPath, forward));
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
