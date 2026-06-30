package com.upgrd.core.model;

import java.util.List;

public record DesignAdvisory(
        String advisoryId,
        String category,
        String file,
        List<Integer> lineRange,
        String smell,
        String suggestion,
        String reason,
        List<String> evidence,
        String risk) {
}
