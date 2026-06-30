package com.upgrd.core.apply;

import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTestGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesHotPathSmokeTests() throws Exception {
        Path appWeb = tempDir.resolve("app-web");
        Path mainJava = appWeb.resolve("src/main/java/com/example");
        Files.createDirectories(mainJava);
        Files.writeString(mainJava.resolve("OrderService.java"), "package com.example; public class OrderService {}");

        UsageReport usage = new UsageReport(1, 5, List.of(
                new UsageHit("com.example.OrderService", 12, "sample")), List.of());

        var result = new SmokeTestGenerator().generate(appWeb, usage);

        assertTrue(result.generatedFiles().size() >= 2);
        assertTrue(Files.isRegularFile(appWeb.resolve("src/test/java/com/upgrd/smoke/OrderServiceSmokeTest.java")));
        assertTrue(result.entryPoints().contains("com.example.OrderService"));
    }
}
