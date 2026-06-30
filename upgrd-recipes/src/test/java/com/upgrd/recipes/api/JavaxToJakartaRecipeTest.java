package com.upgrd.recipes.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaxToJakartaRecipeTest {

    @Test
    void rewritesServletImports() {
        String before = """
                import javax.servlet.http.HttpServletRequest;
                import javax.servlet.http.HttpServletResponse;

                public class Demo {}
                """;

        var change = new JavaxToJakartaRecipe().transform("Demo.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("jakarta.servlet"));
        assertTrue(!after.contains("javax.servlet"));
    }
}
