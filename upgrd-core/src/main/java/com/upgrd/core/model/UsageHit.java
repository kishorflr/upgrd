package com.upgrd.core.model;

public record UsageHit(
        String qualifiedName,
        int hitCount,
        String sample) {
}
