package com.upgrd.recipes.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Log4j1ToSlf4jRecipeTest {

    @Test
    void migratesLoggerDeclaration() {
        String before = """
                import org.apache.log4j.Logger;

                public class UserAction {
                    private static Logger log = Logger.getLogger(UserAction.class);
                }
                """;

        var change = new Log4j1ToSlf4jRecipe().transform("UserAction.java", before);
        assertTrue(change.isPresent());
        String after = change.get().after();
        assertTrue(after.contains("org.slf4j.Logger"));
        assertTrue(after.contains("LoggerFactory.getLogger"));
        assertTrue(!after.contains("org.apache.log4j"));
    }
}
