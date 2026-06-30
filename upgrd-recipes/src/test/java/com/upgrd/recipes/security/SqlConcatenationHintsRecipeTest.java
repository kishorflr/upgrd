package com.upgrd.recipes.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlConcatenationHintsRecipeTest {

    @Test
    void generatesHintFile() {
        String before = """
                public class Dao {
                    void find(String id) {
                        String sql = "SELECT * FROM users WHERE id = '" + id + "';";
                    }
                }
                """;

        var change = new SqlConcatenationHintsRecipe().transform("Dao.java", before);
        assertTrue(change.isPresent());
        assertTrue(change.get().relativePath().endsWith(".sql-refactor-hints.md"));
        assertTrue(change.get().after().contains("PreparedStatement"));
    }
}
