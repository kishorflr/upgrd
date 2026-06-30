package com.upgrd.core.failure;

import com.upgrd.core.model.AnonymousFailureReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts proprietary context from build/test failures while preserving enough
 * technical detail for external AI-assisted troubleshooting.
 */
public final class AnonymousFailureSanitizer {

    private static final String REPORT_VERSION = "1.0";
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?:/[\\w.\\-]+)+|(?:[A-Za-z]:\\\\[\\w.\\-\\\\ ]+)");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|token|jdbc:)[^\\s'\"]*(['\"][^'\"]+['\"]|=\\S+|:\\S+)");
    private static final Pattern QUALIFIED_TYPE = Pattern.compile(
            "\\b([a-z][a-z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+)\\b");
    private static final Pattern STACK_FRAME = Pattern.compile(
            "^\\s+at\\s+([\\w.$]+)\\(([\\w./\\-<>]+):(\\d+)\\)", Pattern.MULTILINE);
    private static final Pattern SUREFIRE_FAILURE = Pattern.compile(
            "\\[ERROR\\]\\s+([\\w.]+)\\.(\\w+)\\s+--\\s+Time elapsed:.*?(?:<<<\\s+(FAILURE!|ERROR!))?",
            Pattern.MULTILINE);
    private static final Pattern FAILURE_MESSAGE = Pattern.compile(
            "(?:org\\.opentest4j\\.|java\\.lang\\.|[\\w.]+Exception:\\s*)(.+)",
            Pattern.MULTILINE);

    private static final Set<String> FRAMEWORK_PREFIXES = Set.of(
            "java.", "javax.", "jakarta.", "org.junit", "org.opentest4j",
            "org.apache.maven", "org.springframework", "org.slf4j", "com.upgrd.smoke");

    private final Map<String, String> tokenMap = new LinkedHashMap<>();
    private int typeCounter = 1;
    private int pathCounter = 1;

    public AnonymousFailureReport sanitize(
            String rawLog,
            String failureKind,
            List<String> appPackagePrefixes,
            Map<String, String> environment) {
        List<String> notes = new ArrayList<>();
        notes.add("Absolute paths replaced with <PATH_n> tokens");
        notes.add("Application package names tokenized as app.Type_n");
        notes.add("Credential-like substrings redacted");
        notes.add("Only failing-test context retained; passing test output omitted");

        String scrubbed = scrubSecrets(rawLog);
        scrubbed = scrubPaths(scrubbed, notes);
        scrubbed = tokenizeApplicationIdentifiers(scrubbed, appPackagePrefixes, notes);

        List<AnonymousFailureReport.SanitizedTestFailure> testFailures = extractTestFailures(scrubbed);
        List<String> stackFrames = extractStackFrames(scrubbed);
        List<String> excerpt = buildExcerpt(scrubbed, testFailures, stackFrames);

        String summary = buildSummary(failureKind, testFailures, scrubbed);
        String prompt = buildAiPrompt(failureKind, summary, testFailures, stackFrames, excerpt, environment, notes);

        return new AnonymousFailureReport(
                REPORT_VERSION,
                Instant.now(),
                failureKind,
                summary,
                testFailures,
                stackFrames,
                excerpt,
                environment == null ? Map.of() : Map.copyOf(environment),
                prompt,
                List.copyOf(notes));
    }

    private String scrubSecrets(String text) {
        return SECRET_PATTERN.matcher(text).replaceAll("$1=<REDACTED>");
    }

    private String scrubPaths(String text, List<String> notes) {
        Matcher matcher = ABSOLUTE_PATH.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group();
            if (path.length() < 4 || path.chars().filter(ch -> ch == '/').count() < 2) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(path));
                continue;
            }
            String token = tokenMap.computeIfAbsent("path:" + path, k -> "<PATH_" + pathCounter++ + ">");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(buffer);
        notes.add("Scrubbed " + pathCounter + " absolute path(s)");
        return buffer.toString();
    }

    private String tokenizeApplicationIdentifiers(String text, List<String> appPackagePrefixes, List<String> notes) {
        Set<String> prefixes = new LinkedHashSet<>(appPackagePrefixes == null ? List.of() : appPackagePrefixes);
        prefixes.addAll(inferPrefixes(text));

        Matcher matcher = QUALIFIED_TYPE.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String qualified = matcher.group(1);
            if (isFrameworkType(qualified)) {
                continue;
            }
            if (!matchesAppPrefix(qualified, prefixes)) {
                continue;
            }
            String token = tokenMap.computeIfAbsent("type:" + qualified, k -> "app.Type_" + typeCounter++);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(buffer);
        notes.add("Tokenized " + (typeCounter - 1) + " application type reference(s)");
        return buffer.toString();
    }

    private Set<String> inferPrefixes(String text) {
        Set<String> prefixes = new LinkedHashSet<>();
        Matcher matcher = QUALIFIED_TYPE.matcher(text);
        while (matcher.find()) {
            String qualified = matcher.group(1);
            if (isFrameworkType(qualified)) {
                continue;
            }
            int lastDot = qualified.lastIndexOf('.');
            if (lastDot > 0) {
                prefixes.add(qualified.substring(0, lastDot));
            }
        }
        return prefixes;
    }

    private boolean matchesAppPrefix(String qualified, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (qualified.startsWith(prefix + ".") || qualified.equals(prefix)) {
                return true;
            }
        }
        return qualified.startsWith("com.") && !isFrameworkType(qualified);
    }

    private boolean isFrameworkType(String qualified) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (qualified.startsWith(prefix)) {
                return true;
            }
        }
        return qualified.startsWith("org.apache.struts")
                || qualified.startsWith("org.apache.log4j")
                || qualified.startsWith("org.hibernate");
    }

    private List<AnonymousFailureReport.SanitizedTestFailure> extractTestFailures(String scrubbed) {
        List<AnonymousFailureReport.SanitizedTestFailure> failures = new ArrayList<>();
        Matcher matcher = SUREFIRE_FAILURE.matcher(scrubbed);
        while (matcher.find()) {
            String testClass = anonymizeSimpleName(matcher.group(1));
            String testMethod = matcher.group(2);
            String message = findNearbyMessage(scrubbed, matcher.start());
            failures.add(new AnonymousFailureReport.SanitizedTestFailure(
                    testClass, testMethod, message, List.of()));
        }
        return failures;
    }

    private String findNearbyMessage(String scrubbed, int anchor) {
        int from = Math.max(0, anchor);
        int to = Math.min(scrubbed.length(), anchor + 1200);
        Matcher matcher = FAILURE_MESSAGE.matcher(scrubbed.substring(from, to));
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Test failed — see stack frames";
    }

    private String anonymizeSimpleName(String qualified) {
        int dot = qualified.lastIndexOf('.');
        String simple = dot >= 0 ? qualified.substring(dot + 1) : qualified;
        if (simple.endsWith("Test") || simple.startsWith("Smoke")) {
            return simple;
        }
        return tokenMap.computeIfAbsent("type:" + qualified, k -> "app.Type_" + typeCounter++);
    }

    private List<String> extractStackFrames(String scrubbed) {
        List<String> frames = new ArrayList<>();
        Matcher matcher = STACK_FRAME.matcher(scrubbed);
        while (matcher.find()) {
            String location = matcher.group(1);
            if (isFrameworkType(location) || location.startsWith("com.upgrd.smoke")) {
                frames.add(matcher.group(0).trim());
            } else if (location.startsWith("app.Type_")) {
                frames.add(matcher.group(0).trim());
            } else if (frames.size() < 12) {
                frames.add("at app.Type_?(" + matcher.group(2) + ":" + matcher.group(3) + ")");
            }
            if (frames.size() >= 15) {
                break;
            }
        }
        return frames;
    }

    private List<String> buildExcerpt(
            String scrubbed,
            List<AnonymousFailureReport.SanitizedTestFailure> failures,
            List<String> stackFrames) {
        List<String> lines = new ArrayList<>();
        for (String line : scrubbed.split("\\R")) {
            if (line.contains("[ERROR]") || line.contains("FAILURE") || line.contains("Exception")
                    || line.contains("Caused by:") || line.startsWith("\tat ")) {
                lines.add(line.trim());
            }
            if (lines.size() >= 40) {
                break;
            }
        }
        if (lines.isEmpty() && !failures.isEmpty()) {
            failures.forEach(f -> lines.add(f.testClass() + "." + f.testMethod() + ": " + f.message()));
        }
        if (lines.isEmpty() && !stackFrames.isEmpty()) {
            lines.addAll(stackFrames);
        }
        return lines;
    }

    private String buildSummary(
            String failureKind,
            List<AnonymousFailureReport.SanitizedTestFailure> testFailures,
            String scrubbed) {
        if (!testFailures.isEmpty()) {
            var first = testFailures.get(0);
            return failureKind + ": " + first.testClass() + "." + first.testMethod() + " — " + first.message();
        }
        if (scrubbed.contains("COMPILATION ERROR")) {
            return failureKind + ": compilation error during build";
        }
        if (scrubbed.contains("BUILD FAILURE")) {
            return failureKind + ": Maven build failure";
        }
        return failureKind + ": verification failed";
    }

    private String buildAiPrompt(
            String failureKind,
            String summary,
            List<AnonymousFailureReport.SanitizedTestFailure> testFailures,
            List<String> stackFrames,
            List<String> excerpt,
            Map<String, String> environment,
            List<String> notes) {
        StringBuilder md = new StringBuilder();
        md.append("# Anonymous failure report (UpGrd)\n\n");
        md.append("Use this prompt with an external AI assistant. Application-specific names and paths are tokenized.\n\n");
        md.append("## Context\n\n");
        md.append("- **Kind:** ").append(failureKind).append("\n");
        md.append("- **Summary:** ").append(summary).append("\n");
        if (environment != null && !environment.isEmpty()) {
            md.append("- **Environment:**\n");
            environment.forEach((k, v) -> md.append("  - ").append(k).append(": ").append(v).append("\n"));
        }
        md.append("\n## Failing tests\n\n");
        if (testFailures.isEmpty()) {
            md.append("_No parsed Surefire failures — see log excerpt._\n\n");
        } else {
            testFailures.forEach(tf -> md.append("- `").append(tf.testClass()).append(".").append(tf.testMethod())
                    .append("`: ").append(tf.message()).append("\n"));
            md.append("\n");
        }
        md.append("## Stack frames (framework + anonymized app)\n\n");
        if (stackFrames.isEmpty()) {
            md.append("_No stack frames captured._\n\n");
        } else {
            stackFrames.forEach(frame -> md.append("- `").append(frame).append("`\n"));
            md.append("\n");
        }
        md.append("## Log excerpt\n\n```\n");
        excerpt.forEach(line -> md.append(line).append("\n"));
        md.append("```\n\n");
        md.append("## Redaction notes\n\n");
        notes.forEach(note -> md.append("- ").append(note).append("\n"));
        md.append("\n## Ask\n\n");
        md.append("Given this anonymized Java/Maven test failure after an UpGrd migration, suggest likely root causes ");
        md.append("and concrete fix steps. Do not ask for proprietary source code; reason from stack frames, ");
        md.append("framework versions, and error messages only.\n");
        return md.toString();
    }
}
