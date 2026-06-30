package com.upgrd.core.antipattern;

import com.upgrd.core.model.AntiPatternFinding;

import java.util.List;
import java.util.Optional;

interface AntiPatternRule {

    String ruleId();

    default String category() {
        return "general";
    }

    List<AntiPatternFinding> detect(String relativePath, String content, IdGenerator ids);

    @FunctionalInterface
    interface IdGenerator {
        String next(String ruleId);
    }

    @FunctionalInterface
    interface Detector {
        List<AntiPatternFinding> detect(String relativePath, String content, IdGenerator ids);
    }

    static AntiPatternRule of(String ruleId, String category, Detector detector) {
        return new AntiPatternRule() {
            @Override
            public String ruleId() {
                return ruleId;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public List<AntiPatternFinding> detect(String relativePath, String content, IdGenerator ids) {
                return detector.detect(relativePath, content, ids);
            }
        };
    }

    static Optional<AntiPatternFinding> single(
            String relativePath,
            List<Integer> lineRange,
            AntiPatternRule rule,
            String pattern,
            String severity,
            String suggestion,
            String rationale,
            List<String> evidence,
            IdGenerator ids) {
        return Optional.of(new AntiPatternFinding(
                ids.next(rule.ruleId()),
                rule.ruleId(),
                rule.category(),
                relativePath,
                lineRange,
                pattern,
                severity,
                suggestion,
                rationale,
                evidence));
    }
}
