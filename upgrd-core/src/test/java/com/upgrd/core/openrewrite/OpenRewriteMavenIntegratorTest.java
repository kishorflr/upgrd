package com.upgrd.core.openrewrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRewriteMavenIntegratorTest {

    @TempDir
    Path tempDir;

    @Test
    void injectsRewritePluginAndBom() throws Exception {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0-SNAPSHOT</version>
                  <packaging>pom</packaging>
                  <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                  </properties>
                  <modules>
                    <module>app-web</module>
                  </modules>
                </project>
                """);

        new OpenRewriteMavenIntegrator().ensurePluginConfigured(tempDir);
        String after = Files.readString(pom);

        assertTrue(after.contains("rewrite-maven-plugin"));
        assertTrue(after.contains("rewrite-recipe-bom"));
        assertTrue(after.contains("rewrite-migrate-java"));
        assertTrue(after.contains("com.upgrd.migrated.UpgradeBaseline"));
    }
}
