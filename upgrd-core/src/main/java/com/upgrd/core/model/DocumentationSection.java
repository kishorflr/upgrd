package com.upgrd.core.model;

import java.util.List;

public record DocumentationSection(
        String sectionId,
        String title,
        String category,
        String phase,
        String content,
        List<String> relatedFiles) {
}
