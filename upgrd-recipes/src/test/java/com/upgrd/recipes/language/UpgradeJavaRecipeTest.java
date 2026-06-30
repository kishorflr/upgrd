package com.upgrd.recipes.language;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeJavaRecipeTest {

    @Test
    void replacesLegacyConstructorsAndCollections() {
        String before = """
                package com.example;
                import java.util.Vector;
                public class Legacy {
                    Integer count = new Integer(1);
                    Vector items = new Vector();
                }
                """;

        var change = new UpgradeJavaRecipe().transform("Legacy.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("Integer.valueOf(1)"));
        assertTrue(after.contains("ArrayList"));
        assertTrue(!after.contains("new Integer("));
    }
}
