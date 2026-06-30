package com.upgrd.recipes.openrewrite;

import java.util.List;
import java.util.Map;

/**
 * Maps OpenRewrite coordinates to UpGrd-native {@link com.upgrd.recipes.FileRecipe} implementations
 * until the OpenRewrite Maven plugin is wired for full AST rewrites.
 */
public final class OpenRewriteCatalog {

    private static final Map<String, String> DELEGATED_TO_UPGRD = Map.of(
            "org.openrewrite.java.migrate.UpgradeToJava21", "upgrd:UpgradeToJava21",
            "org.openrewrite.java.spring.framework.UpgradeSpringFramework_6_0", "upgrd:Spring4To6",
            "org.openrewrite.staticanalysis.CommonStaticAnalysis", "upgrd:ReplaceRawCollections");

    private OpenRewriteCatalog() {
    }

    public static String upgrdEquivalent(String openRewriteCoordinate) {
        return DELEGATED_TO_UPGRD.get(openRewriteCoordinate);
    }

    public static List<String> pendingOpenRewriteRecipes() {
        return List.of(
                "org.openrewrite.java.spring.boot3.SpringBoot3BestPractices",
                "org.openrewrite.java.testing.junit5.JUnit4to5Migration");
    }
}
