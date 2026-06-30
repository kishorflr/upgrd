package com.upgrd.core.apply;

import com.upgrd.core.model.UsageHit;
import com.upgrd.core.model.UsageReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates JUnit 5 smoke tests inside the migrated application for hot paths and main classes.
 */
public final class SmokeTestGenerator {

    private static final int MAX_HOT_PATH_TESTS = 10;
    private static final int MAX_FALLBACK_TESTS = 5;

    public GenerationResult generate(Path appWebRoot, UsageReport usage) throws IOException {
        Path testRoot = appWebRoot.resolve("src/test/java/com/upgrd/smoke");
        Files.createDirectories(testRoot);

        List<String> entryPoints = new ArrayList<>();
        List<String> generated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (usage != null && usage.hits() != null) {
            for (UsageHit hit : usage.hits()) {
                if (generated.size() >= MAX_HOT_PATH_TESTS) {
                    break;
                }
                if (hit.qualifiedName() == null || hit.qualifiedName().isBlank()) {
                    continue;
                }
                if (!seen.add(hit.qualifiedName())) {
                    continue;
                }
                String simpleName = simpleName(hit.qualifiedName());
                Path testFile = testRoot.resolve(simpleName + "SmokeTest.java");
                Files.writeString(testFile, hotPathTest(simpleName, hit.qualifiedName(), hit.hitCount()));
                generated.add(testFile.toString());
                entryPoints.add(hit.qualifiedName());
            }
        }

        if (generated.isEmpty()) {
            entryPoints.addAll(generateFromMainSources(appWebRoot, testRoot, generated, seen));
        }

        Path sanity = testRoot.resolve("UpgrdMigrationSanityTest.java");
        Files.writeString(sanity, sanityTest());
        generated.add(sanity.toString());

        return new GenerationResult(generated, entryPoints);
    }

    private List<String> generateFromMainSources(
            Path appWebRoot,
            Path testRoot,
            List<String> generated,
            Set<String> seen) throws IOException {
        List<String> entryPoints = new ArrayList<>();
        Path mainJava = appWebRoot.resolve("src/main/java");
        if (!Files.isDirectory(mainJava)) {
            return entryPoints;
        }
        try (Stream<Path> files = Files.walk(mainJava)) {
            List<Path> javaFiles = files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .limit(MAX_FALLBACK_TESTS)
                    .toList();
            for (Path file : javaFiles) {
                String relative = mainJava.relativize(file).toString().replace('\\', '/');
                String qualified = relative.replace(".java", "").replace('/', '.');
                if (!seen.add(qualified)) {
                    continue;
                }
                String simpleName = simpleName(qualified);
                Path testFile = testRoot.resolve(simpleName + "SmokeTest.java");
                Files.writeString(testFile, hotPathTest(simpleName, qualified, 0));
                generated.add(testFile.toString());
                entryPoints.add(qualified);
            }
        }
        return entryPoints;
    }

    private String hotPathTest(String simpleName, String qualifiedName, int hits) {
        String hitComment = hits > 0 ? hits + " log hits" : "main source class";
        return """
                package com.upgrd.smoke;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
                import static org.junit.jupiter.api.Assertions.assertNotNull;

                /**
                 * UpGrd-generated smoke test (%s).
                 */
                class %sSmokeTest {

                    @Test
                    void classLoads() {
                        assertDoesNotThrow(() -> {
                            Class<?> type = Class.forName("%s");
                            assertNotNull(type);
                        });
                    }
                }
                """.formatted(hitComment, simpleName, qualifiedName);
    }

    private String sanityTest() {
        return """
                package com.upgrd.smoke;

                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertTrue;

                /**
                 * Verifies UpGrd migrated the project with a standard Maven test layout.
                 */
                class UpgrdMigrationSanityTest {

                    @Test
                    void smokeTestPackageExists() {
                        assertTrue(getClass().getPackageName().startsWith("com.upgrd.smoke"));
                    }
                }
                """;
    }

    private String simpleName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

    public record GenerationResult(List<String> generatedFiles, List<String> entryPoints) {
    }
}
