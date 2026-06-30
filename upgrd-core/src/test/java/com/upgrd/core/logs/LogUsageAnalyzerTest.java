package com.upgrd.core.logs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUsageAnalyzerTest {

    private final LogUsageAnalyzer analyzer = new LogUsageAnalyzer();

    @Test
    void detectsStackTraceAndServletPaths() throws Exception {
        Path log = Files.createTempFile("upgrd-log", ".log");
        Files.writeString(log, """
                GET /api/orders 200
                ERROR java.lang.RuntimeException: failed
                    at com.example.orders.OrderService.process(OrderService.java:42)
                com.example.orders.OrderService - handled request
                """);

        var report = analyzer.analyze(List.of(log), Set.of("com.example.orders.OrderService"));

        assertTrue(report.totalHits() > 0);
        assertTrue(report.hits().stream().anyMatch(hit -> hit.qualifiedName().contains("OrderService")
                || hit.qualifiedName().startsWith("path:")));
    }
}
