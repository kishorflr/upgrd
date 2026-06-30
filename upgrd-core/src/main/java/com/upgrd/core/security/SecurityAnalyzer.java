package com.upgrd.core.security;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.ProjectDiscovery;
import com.upgrd.core.model.SecurityFinding;
import com.upgrd.core.model.SecurityReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detects known vulnerability patterns in legacy source and classpath jars.
 */
public final class SecurityAnalyzer {

    private static final Pattern SECRET_PROPERTY = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\s*=\\s*[^\\s#$\\{].+");
    private static final Pattern SQL_CONCAT = Pattern.compile(
            "\"\\s*SELECT .+\"\\s*\\+");
    private static final Pattern WEAK_HASH = Pattern.compile(
            "MessageDigest\\.getInstance\\(\"(MD5|SHA-1|SHA1)\"\\)");
    private static final Pattern UNSAFE_DESERIAL = Pattern.compile(
            "new ObjectInputStream\\(");

    public SecurityReport analyze(Path sourceRoot, ProjectDiscovery discovery) throws IOException {
        AtomicInteger counter = new AtomicInteger();
        List<SecurityFinding> findings = new ArrayList<>();

        scanClasspathJars(sourceRoot, findings, counter);
        scanSourceFiles(sourceRoot, discovery.sourceRoots(), findings, counter);

        int open = (int) findings.stream().filter(f -> !f.remediated()).count();
        return new SecurityReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                discovery.profile(),
                List.copyOf(findings),
                0,
                open);
    }

    private void scanClasspathJars(Path sourceRoot, List<SecurityFinding> findings, AtomicInteger counter) {
        for (String evidence : discoveryJars(sourceRoot)) {
            String jar = evidence.toLowerCase();
            if (jar.contains("log4j-1") || jar.equals("classpath:log4j.jar")) {
                findings.add(finding(counter, "CRITICAL", "dependency", "CVE-2019-17571",
                        "(classpath)", List.of(), "Log4j 1.x JAR on classpath — EOL with known RCE CVEs",
                        "Migrate to SLF4J via upgrd:Log4j1ToSlf4j", "upgrd:Log4j1ToSlf4j", true));
            }
            if (jar.contains("commons-collections-3.")) {
                findings.add(finding(counter, "HIGH", "dependency", "CVE-2015-7501",
                        "(classpath)", List.of(), "commons-collections 3.x enables deserialization gadget chains",
                        "Remove or upgrade to commons-collections4", "upgrd:RemoveVulnerableDependency", true));
            }
            if (jar.contains("struts") && (jar.contains("1.") || jar.contains("2.3."))) {
                findings.add(finding(counter, "HIGH", "dependency", "CVE-2017-5638",
                        "(classpath)", List.of(), "Outdated Struts version with known remote code execution history",
                        "Migrate Struts actions to Spring MVC", "upgrd:StrutsActionToSpringController", true));
            }
        }
    }

    private List<String> discoveryJars(Path sourceRoot) {
        List<String> jars = new ArrayList<>();
        for (Path lib : List.of(
                sourceRoot.resolve("lib"),
                sourceRoot.resolve("WEB-INF/lib"),
                sourceRoot.resolve("WebContent/WEB-INF/lib"))) {
            if (!Files.isDirectory(lib)) {
                continue;
            }
            try (Stream<Path> list = Files.list(lib)) {
                list.filter(p -> p.toString().endsWith(".jar"))
                        .forEach(p -> jars.add("classpath:" + p.getFileName()));
            } catch (IOException ignored) {
                // skip unreadable lib folder
            }
        }
        return jars;
    }

    private void scanSourceFiles(
            Path sourceRoot,
            List<String> sourceRoots,
            List<SecurityFinding> findings,
            AtomicInteger counter) throws IOException {
        for (String sourceRootRel : sourceRoots) {
            Path root = sourceRoot.resolve(sourceRootRel);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile).forEach(path -> scanFile(path, sourceRoot, findings, counter));
            }
        }
    }

    private void scanFile(Path file, Path sourceRoot, List<SecurityFinding> findings, AtomicInteger counter) {
        try {
            String content = Files.readString(file);
            String rel = sourceRoot.relativize(file).toString();
            List<String> lines = content.lines().toList();

            if (file.toString().endsWith(".properties") || file.toString().endsWith(".xml")) {
                if (SECRET_PROPERTY.matcher(content).find()) {
                    findings.add(finding(counter, "HIGH", "secret", null, rel, List.of(1, lines.size()),
                            "Hardcoded credential in configuration file",
                            "Replace with environment variable placeholder", "upgrd:ExternalizeSecrets", true));
                }
            }

            if (!file.toString().endsWith(".java")) {
                return;
            }

            if (content.contains("org.apache.log4j")) {
                findings.add(finding(counter, "CRITICAL", "logging", "CVE-2019-17571", rel,
                        lineRangeOf(content, "log4j"), "Log4j 1.x API usage in source",
                        "Migrate to SLF4J", "upgrd:Log4j1ToSlf4j", true));
            }

            var weakHash = WEAK_HASH.matcher(content);
            while (weakHash.find()) {
                int line = lineNumber(content, weakHash.start());
                findings.add(finding(counter, "MEDIUM", "crypto", null, rel, List.of(line, line),
                        "Weak hash algorithm (" + weakHash.group(1) + ") — unsuitable for security-sensitive use",
                        "Replace with SHA-256 or stronger", "upgrd:RemediateWeakHash", true));
            }

            if (SQL_CONCAT.matcher(content).find()) {
                int line = lineOf(content, "SELECT");
                findings.add(finding(counter, "HIGH", "injection", "CWE-89", rel, List.of(line, line + 5),
                        "SQL built via string concatenation — SQL injection risk",
                        "Use PreparedStatement with bound parameters", "upgrd:RemediateSqlConcatenation", false));
            }

            if (UNSAFE_DESERIAL.matcher(content).find()) {
                int line = lineOf(content, "ObjectInputStream");
                findings.add(finding(counter, "HIGH", "deserialization", "CWE-502", rel, List.of(line, line),
                        "Java deserialization without validation",
                        "Replace with JSON/XML parser or validate stream", "upgrd:RemediateDeserialization", false));
            }
        } catch (IOException ignored) {
            // skip unreadable files
        }
    }

    private SecurityFinding finding(
            AtomicInteger counter,
            String severity,
            String category,
            String cveId,
            String file,
            List<Integer> lineRange,
            String description,
            String remediation,
            String recipeId,
            boolean autoFixable) {
        return new SecurityFinding(
                "sec-" + String.format("%04d", counter.incrementAndGet()),
                severity,
                category,
                cveId,
                file,
                lineRange,
                description,
                remediation,
                recipeId,
                autoFixable,
                false);
    }

    private int lineOf(String content, String token) {
        int idx = content.indexOf(token);
        return idx >= 0 ? lineNumber(content, idx) : 1;
    }

    private int lineNumber(String content, int index) {
        return (int) content.substring(0, index).chars().filter(ch -> ch == '\n').count() + 1;
    }

    private List<Integer> lineRangeOf(String content, String token) {
        return List.of(lineOf(content, token));
    }
}
