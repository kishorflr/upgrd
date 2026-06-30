package com.upgrd.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAnalyzerTest {

    private final SecurityAnalyzer analyzer = new SecurityAnalyzer();

    @TempDir
    Path tempDir;

    @Test
    void detectsLog4jAndWeakHash() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), """
                import org.apache.log4j.Logger;
                import java.security.MessageDigest;
                public class App {
                    private static Logger log = Logger.getLogger(App.class);
                    void hash() throws Exception {
                        MessageDigest.getInstance("MD5");
                    }
                }
                """);

        var discovery = new com.upgrd.core.discovery.ProjectDiscoveryService().discover(tempDir);
        var report = analyzer.analyze(tempDir, discovery);

        assertTrue(report.findings().stream().anyMatch(f -> "CVE-2019-17571".equals(f.cveId())));
        assertTrue(report.findings().stream().anyMatch(f -> "crypto".equals(f.category())));
        assertTrue(report.openCount() >= 2);
    }
}
