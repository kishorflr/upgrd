package com.upgrd.core.apply;

import com.upgrd.core.process.MavenCommand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ensures migrated app-web POM declares dependencies implied by Java imports
 * (compile-closure after framework migration recipes).
 */
public final class CompileClosureService {

    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+);\\s*$");
    private static final Pattern DEPENDENCY_BLOCK = Pattern.compile(
            "<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>",
            Pattern.DOTALL);

    public ClosureResult close(Path migratedRoot) throws IOException {
        Path appWebRoot = migratedRoot.resolve("app-web");
        if (!Files.isRegularFile(appWebRoot.resolve("pom.xml"))) {
            return ClosureResult.skipped("No app-web/pom.xml");
        }

        Set<String> imports = collectImports(appWebRoot.resolve("src/main/java"));
        List<String> added = patchPom(appWebRoot.resolve("pom.xml"), imports);

        CompileAttempt compile = attemptCompile(migratedRoot);
        return new ClosureResult(true, added, compile.attempted(), compile.passed(), compile.logTail());
    }

    private Set<String> collectImports(Path javaRoot) throws IOException {
        Set<String> imports = new LinkedHashSet<>();
        if (!Files.isDirectory(javaRoot)) {
            return imports;
        }
        try (Stream<Path> walk = Files.walk(javaRoot)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            for (String line : Files.readString(p).split("\n")) {
                                Matcher matcher = IMPORT.matcher(line.trim());
                                if (matcher.matches()) {
                                    imports.add(matcher.group(1));
                                }
                            }
                        } catch (IOException ignored) {
                            // skip unreadable file
                        }
                    });
        }
        return imports;
    }

    private List<String> patchPom(Path pom, Set<String> imports) throws IOException {
        String content = Files.readString(pom);
        List<DependencySpec> needed = requiredDependencies(imports);
        List<String> added = new ArrayList<>();
        String updated = content;
        for (DependencySpec dep : needed) {
            if (hasDependency(updated, dep.groupId(), dep.artifactId())) {
                continue;
            }
            updated = insertBeforeClosingDependencies(updated, dep.xml());
            added.add(dep.groupId() + ":" + dep.artifactId());
        }
        if (!added.isEmpty()) {
            Files.writeString(pom, updated);
        }
        return added;
    }

    private List<DependencySpec> requiredDependencies(Set<String> imports) {
        List<DependencySpec> specs = new ArrayList<>();
        if (imports.stream().anyMatch(i -> i.startsWith("jakarta.validation"))) {
            specs.add(new DependencySpec(
                    "jakarta.validation",
                    "jakarta.validation-api",
                    "3.0.2",
                    """
                            <dependency>
                              <groupId>jakarta.validation</groupId>
                              <artifactId>jakarta.validation-api</artifactId>
                              <version>3.0.2</version>
                            </dependency>
                            """));
        }
        if (imports.stream().anyMatch(i -> i.startsWith("org.thymeleaf"))) {
            specs.add(new DependencySpec(
                    "org.thymeleaf",
                    "thymeleaf-spring6",
                    "3.1.3.RELEASE",
                    """
                            <dependency>
                              <groupId>org.thymeleaf</groupId>
                              <artifactId>thymeleaf-spring6</artifactId>
                              <version>3.1.3.RELEASE</version>
                            </dependency>
                            """));
        }
        return specs;
    }

    private boolean hasDependency(String pom, String groupId, String artifactId) {
        Matcher matcher = DEPENDENCY_BLOCK.matcher(pom);
        while (matcher.find()) {
            if (groupId.equals(matcher.group(1).trim()) && artifactId.equals(matcher.group(2).trim())) {
                return true;
            }
        }
        return false;
    }

    private String insertBeforeClosingDependencies(String pom, String dependencyXml) {
        int idx = pom.lastIndexOf("  </dependencies>");
        if (idx < 0) {
            return pom;
        }
        return pom.substring(0, idx) + "\n" + dependencyXml + pom.substring(idx);
    }

    private CompileAttempt attemptCompile(Path migratedRoot) {
        Path migratedPom = migratedRoot.resolve("pom.xml");
        if (!Files.isRegularFile(migratedPom)) {
            return CompileAttempt.skipped();
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    MavenCommand.executable(), "-f", migratedPom.toString(), "compile", "-q");
            builder.directory(migratedRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String log = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            return new CompileAttempt(true, exit == 0, tail(log, 30));
        } catch (Exception ex) {
            return new CompileAttempt(true, false, "mvn compile failed to start: " + ex.getMessage());
        }
    }

    private String tail(String log, int lines) {
        if (log == null || log.isBlank()) {
            return "";
        }
        String[] parts = log.split("\n");
        int start = Math.max(0, parts.length - lines);
        return String.join("\n", java.util.Arrays.copyOfRange(parts, start, parts.length));
    }

    private record DependencySpec(String groupId, String artifactId, String version, String xml) {
    }

    private record CompileAttempt(boolean attempted, boolean passed, String logTail) {
        static CompileAttempt skipped() {
            return new CompileAttempt(false, false, "");
        }
    }

    public record ClosureResult(
            boolean ran,
            List<String> dependenciesAdded,
            boolean compileAttempted,
            boolean compilePassed,
            String compileLogTail) {

        static ClosureResult skipped(String reason) {
            return new ClosureResult(false, List.of(), false, false, reason);
        }
    }
}
