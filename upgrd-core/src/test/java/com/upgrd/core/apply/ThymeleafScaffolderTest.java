package com.upgrd.core.apply;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThymeleafScaffolderTest {

    @TempDir
    Path tempDir;

    @Test
    void wiresThymeleafWhenTemplatesPresent() throws Exception {
        Path appWeb = tempDir.resolve("app-web");
        Path template = appWeb.resolve("src/main/resources/templates/pages/login.html");
        Files.createDirectories(template.getParent());
        Files.writeString(template, "<html><body>login</body></html>");
        Files.createDirectories(appWeb.resolve("src/main/java"));
        Files.writeString(appWeb.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <dependencies>
                  </dependencies>
                </project>
                """);

        var artifacts = new ThymeleafScaffolder().scaffold(appWeb);
        assertTrue(artifacts.size() >= 2);
        assertTrue(Files.readString(appWeb.resolve("pom.xml")).contains("thymeleaf-spring6"));
        assertTrue(Files.isRegularFile(
                appWeb.resolve("src/main/java/com/example/config/WebMvcThymeleafConfig.java")));
    }
}
