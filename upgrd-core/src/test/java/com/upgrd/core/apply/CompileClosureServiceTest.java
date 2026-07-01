package com.upgrd.core.apply;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileClosureServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void addsValidationApiWhenJakartaValidationImported() throws Exception {
        Path migrated = tempDir.resolve("migrated");
        Path appWeb = migrated.resolve("app-web");
        Path javaDir = appWeb.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);
        Files.writeString(javaDir.resolve("LoginAction.java"), """
                package com.example;
                import jakarta.validation.Valid;
                import org.springframework.stereotype.Controller;
                @Controller
                public class LoginAction {
                    public void submit(@Valid LoginForm form) {}
                }
                """);
        Files.writeString(migrated.resolve("pom.xml"), parentPom());
        Files.writeString(appWeb.resolve("pom.xml"), appWebPomWithoutValidation());

        CompileClosureService.ClosureResult result = new CompileClosureService().close(migrated);

        assertTrue(result.dependenciesAdded().stream()
                .anyMatch(d -> d.contains("jakarta.validation-api")));
        String pom = Files.readString(appWeb.resolve("pom.xml"));
        assertTrue(pom.contains("jakarta.validation-api"));
    }

    private String parentPom() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>migrated-parent</artifactId>
                  <version>1.0.0-SNAPSHOT</version>
                  <packaging>pom</packaging>
                  <modules><module>app-web</module></modules>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                  </properties>
                </project>
                """;
    }

    private String appWebPomWithoutValidation() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>migrated-parent</artifactId>
                    <version>1.0.0-SNAPSHOT</version>
                  </parent>
                  <artifactId>app-web</artifactId>
                  <packaging>war</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework</groupId>
                      <artifactId>spring-webmvc</artifactId>
                      <version>6.2.1</version>
                    </dependency>
                  </dependencies>
                </project>
                """;
    }
}
