package com.upgrd.core.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnologyFingerprintScannerTest {

    private final TechnologyFingerprintScanner scanner = new TechnologyFingerprintScanner();

    @TempDir
    Path tempDir;

    @Test
    void detectsLegacyWebStack() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("UserAction.java"), """
                import org.apache.log4j.Logger;
                import org.springframework.web.bind.annotation.RequestMapping;
                import javax.servlet.http.HttpServletRequest;
                public class UserAction {
                    private static Logger log = Logger.getLogger(UserAction.class);
                }
                """);
        Files.createDirectories(tempDir.resolve("WEB-INF"));
        Files.writeString(tempDir.resolve("WEB-INF/web.xml"), "<web-app/>");

        var fp = scanner.scan(tempDir, List.of("src"));
        assertTrue(fp.frameworks().contains("SPRING_MVC_4"));
        assertEquals(com.upgrd.core.model.LoggingFramework.LOG4J_1, fp.logging());
        assertEquals(com.upgrd.core.model.ServletApi.JAVAX, fp.servletApi());
        assertEquals(com.upgrd.core.model.ProjectProfile.LEGACY_WEB,
                scanner.inferProfile(fp, List.of("WEB-INF/web.xml")));
    }

    @Test
    void detectsLegacyBackendSignals() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        StringBuilder big = new StringBuilder("public class LegacyService {\n");
        for (int i = 0; i < 520; i++) {
            big.append("  void m").append(i).append("() {}\n");
        }
        big.append("}\n");
        Files.writeString(src.resolve("LegacyService.java"), big.toString());

        var fp = scanner.scan(tempDir, List.of("src"));
        assertTrue(fp.riskSignals().contains("god-class"));
        assertEquals(com.upgrd.core.model.ProjectProfile.LEGACY_BACKEND,
                scanner.inferProfile(fp, List.of()));
    }
}
