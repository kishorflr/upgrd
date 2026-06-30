package com.upgrd.core.antipattern;

import com.upgrd.core.model.ProjectProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiPatternAnalyzerTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsCatchAndSwallowAndSystemOut() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("BadService.java"), """
                public class BadService {
                    void run() {
                        try {
                            doWork();
                        } catch (Exception e) {
                        }
                        System.out.println("done");
                    }
                    void doWork() {}
                }
                """);

        var report = new AntiPatternAnalyzer().analyze(tempDir, List.of("src"), ProjectProfile.LEGACY_BACKEND);

        assertTrue(report.totalFindings() >= 2);
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("catch-and-swallow")));
        assertTrue(report.findings().stream().anyMatch(f -> f.ruleId().equals("system-out-logging")));
    }
}
