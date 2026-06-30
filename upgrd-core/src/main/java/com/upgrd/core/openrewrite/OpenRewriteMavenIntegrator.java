package com.upgrd.core.openrewrite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Injects OpenRewrite Maven plugin and recipe dependencies into migrated POMs.
 */
public final class OpenRewriteMavenIntegrator {

    private static final String RECIPE_BOM_VERSION = "3.33.0";
    private static final String PLUGIN_VERSION = "6.42.0";

    private static final String PLUGIN_MARKER = "<artifactId>rewrite-maven-plugin</artifactId>";
    private static final String BOM_MARKER = "<artifactId>rewrite-recipe-bom</artifactId>";

    public static String recipeBomVersion() {
        return RECIPE_BOM_VERSION;
    }

    public static String pluginVersion() {
        return PLUGIN_VERSION;
    }

    public void ensurePluginConfigured(Path migratedRoot) throws IOException {
        Path pom = migratedRoot.resolve("pom.xml");
        String content = Files.readString(pom);
        if (content.contains(PLUGIN_MARKER) && content.contains(BOM_MARKER)) {
            return;
        }
        Files.writeString(pom, inject(content));
    }

    private String inject(String pom) {
        if (pom.contains(BOM_MARKER)) {
            return injectPluginOnly(pom);
        }

        String propertiesBlock = """
                  <rewrite-recipe-bom.version>%s</rewrite-recipe-bom.version>
                  <rewrite-maven-plugin.version>%s</rewrite-maven-plugin.version>
                """.formatted(RECIPE_BOM_VERSION, PLUGIN_VERSION);
        String withProps = pom.replace(
                "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>",
                "<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" + propertiesBlock);

        String bom = """
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>org.openrewrite.recipe</groupId>
                        <artifactId>rewrite-recipe-bom</artifactId>
                        <version>${rewrite-recipe-bom.version}</version>
                        <type>pom</type>
                        <scope>import</scope>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.openrewrite.recipe</groupId>
                      <artifactId>rewrite-migrate-java</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.openrewrite.recipe</groupId>
                      <artifactId>rewrite-spring</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.openrewrite.recipe</groupId>
                      <artifactId>rewrite-static-analysis</artifactId>
                    </dependency>
                  </dependencies>
                """;

        String withBom = withProps.replace("<modules>", bom + "  <modules>");
        return injectPluginOnly(withBom);
    }

    private String injectPluginOnly(String pom) {
        if (pom.contains(PLUGIN_MARKER)) {
            return pom;
        }
        String plugin = """
                  <build>
                    <pluginManagement>
                      <plugins>
                        <plugin>
                          <groupId>org.openrewrite.maven</groupId>
                          <artifactId>rewrite-maven-plugin</artifactId>
                          <version>${rewrite-maven-plugin.version}</version>
                          <configuration>
                            <configLocation>.upgrd/openrewrite.yml</configLocation>
                            <activeRecipes>
                              <recipe>com.upgrd.migrated.UpgradeBaseline</recipe>
                            </activeRecipes>
                          </configuration>
                        </plugin>
                      </plugins>
                    </pluginManagement>
                  </build>
                """;
        return pom.replace("</project>", plugin + "</project>");
    }
}
