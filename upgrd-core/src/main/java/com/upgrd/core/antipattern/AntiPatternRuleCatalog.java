package com.upgrd.core.antipattern;

import com.upgrd.core.model.AntiPatternFinding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AntiPatternRuleCatalog {

    private static final int GOD_CLASS_LINES = 500;
    private static final int LONG_METHOD_LINES = 80;

    private AntiPatternRuleCatalog() {
    }

    static List<AntiPatternRule> rules() {
        return List.of(
                godClassRule(),
                threadUnsafeSingletonRule(),
                mutableStaticStateRule(),
                catchAndSwallowRule(),
                sqlConcatenationRule(),
                unsafeDeserializationRule(),
                emptyCatchRule(),
                systemOutRule());
    }

    private static AntiPatternRule godClassRule() {
        return AntiPatternRule.of("god-class", "structure", (rel, content, ids) -> {
            int lines = content.lines().toList().size();
            if (lines <= GOD_CLASS_LINES) {
                return List.of();
            }
            return List.of(AntiPatternRule.single(
                    rel, List.of(1, lines), rule("god-class", "structure"),
                    "god-class", "MEDIUM",
                    "Split into focused classes by responsibility",
                    "Classes over " + GOD_CLASS_LINES + " lines are hard to test and migrate safely",
                    List.of(rel + ": " + lines + " lines"), ids).orElseThrow());
        });
    }

    private static AntiPatternRule threadUnsafeSingletonRule() {
        Pattern pattern = Pattern.compile(
                "static\\s+\\w+\\s+instance\\s*;[\\s\\S]{0,400}getInstance\\s*\\(",
                Pattern.CASE_INSENSITIVE);
        return AntiPatternRule.of("thread-unsafe-singleton", "concurrency", (rel, content, ids) -> {
            if (!pattern.matcher(content).find()) {
                return List.of();
            }
            return List.of(AntiPatternRule.single(
                    rel, List.of(1, (int) Math.min(content.lines().count(), 40)),
                    rule("thread-unsafe-singleton", "concurrency"),
                    "thread-unsafe-singleton", "HIGH",
                    "Use enum singleton, holder idiom, or inject a scoped bean",
                    "Lazy singletons without synchronization or volatile fields are unsafe under concurrent load",
                    List.of(rel + ": getInstance() with static instance field"), ids).orElseThrow());
        });
    }

    private static AntiPatternRule mutableStaticStateRule() {
        Pattern mutable = Pattern.compile("static\\s+(?!final)[\\w<>,\\[\\]\\s]+\\s+\\w+\\s*=");
        return AntiPatternRule.of("mutable-static-state", "concurrency", (rel, content, ids) -> {
            if (!mutable.matcher(content).find()) {
                return List.of();
            }
            return List.of(AntiPatternRule.single(
                    rel, List.of(1, 30), rule("mutable-static-state", "concurrency"),
                    "mutable-static-state", "HIGH",
                    "Prefer dependency injection or make fields final/immutable",
                    "Mutable static fields complicate testing and introduce thread-safety risks",
                    List.of(rel + ": non-final static field assignment"), ids).orElseThrow());
        });
    }

    private static AntiPatternRule catchAndSwallowRule() {
        Pattern swallow = Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}");
        return AntiPatternRule.of("catch-and-swallow", "reliability", (rel, content, ids) -> {
            Matcher matcher = swallow.matcher(content);
            if (!matcher.find()) {
                return List.of();
            }
            int line = lineNumber(content, matcher.start());
            return List.of(AntiPatternRule.single(
                    rel, List.of(line, line + 2), rule("catch-and-swallow", "reliability"),
                    "catch-and-swallow", "MEDIUM",
                    "Log at appropriate level and rethrow or handle explicitly",
                    "Empty catch blocks hide failures and make production debugging difficult",
                    List.of(rel + ": empty catch at line " + line), ids).orElseThrow());
        });
    }

    private static AntiPatternRule emptyCatchRule() {
        return AntiPatternRule.of("catch-print-stacktrace", "reliability", (rel, content, ids) -> {
            if (!content.contains("printStackTrace()")) {
                return List.of();
            }
            int line = lineOf(content, "printStackTrace");
            return List.of(AntiPatternRule.single(
                    rel, List.of(line, line), rule("catch-print-stacktrace", "reliability"),
                    "catch-print-stacktrace", "LOW",
                    "Use structured logging (SLF4J) instead of printStackTrace()",
                    "printStackTrace() bypasses logging configuration and is unsuitable for production",
                    List.of(rel + ": printStackTrace() at line " + line), ids).orElseThrow());
        });
    }

    private static AntiPatternRule sqlConcatenationRule() {
        Pattern sql = Pattern.compile("\"\\s*SELECT .+\"\\s*\\+", Pattern.CASE_INSENSITIVE);
        return AntiPatternRule.of("sql-concatenation", "security", (rel, content, ids) -> {
            if (!sql.matcher(content).find()) {
                return List.of();
            }
            int line = lineOf(content, "SELECT");
            return List.of(AntiPatternRule.single(
                    rel, List.of(line, line + 3), rule("sql-concatenation", "security"),
                    "sql-concatenation", "HIGH",
                    "Use PreparedStatement with bound parameters",
                    "Dynamic SQL via string concatenation enables SQL injection (CWE-89)",
                    List.of(rel + ": concatenated SELECT"), ids).orElseThrow());
        });
    }

    private static AntiPatternRule unsafeDeserializationRule() {
        return AntiPatternRule.of("unsafe-deserialization", "security", (rel, content, ids) -> {
            if (!content.contains("ObjectInputStream")) {
                return List.of();
            }
            int line = lineOf(content, "ObjectInputStream");
            return List.of(AntiPatternRule.single(
                    rel, List.of(line, line), rule("unsafe-deserialization", "security"),
                    "unsafe-deserialization", "HIGH",
                    "Replace with JSON/XML parsing or validate with an allowlist",
                    "Untrusted deserialization can lead to remote code execution (CWE-502)",
                    List.of(rel + ": ObjectInputStream at line " + line), ids).orElseThrow());
        });
    }

    private static AntiPatternRule systemOutRule() {
        return AntiPatternRule.of("system-out-logging", "logging", (rel, content, ids) -> {
            if (!content.contains("System.out.print")) {
                return List.of();
            }
            int line = lineOf(content, "System.out");
            return List.of(AntiPatternRule.single(
                    rel, List.of(line, line), rule("system-out-logging", "logging"),
                    "system-out-logging", "LOW",
                    "Use SLF4J logger instead of System.out/err",
                    "Console logging bypasses log levels and centralized observability",
                    List.of(rel + ": System.out at line " + line), ids).orElseThrow());
        });
    }

    private static AntiPatternRule rule(String id, String category) {
        return AntiPatternRule.of(id, category, (rel, content, ids) -> List.of());
    }

    private static int lineOf(String content, String token) {
        int idx = content.indexOf(token);
        return idx >= 0 ? lineNumber(content, idx) : 1;
    }

    private static int lineNumber(String content, int index) {
        return (int) content.substring(0, index).chars().filter(ch -> ch == '\n').count() + 1;
    }
}
