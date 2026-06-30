package com.upgrd.recipes.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsafeDeserializationHintsRecipeTest {

    @Test
    void generatesHintsForObjectInputStream() {
        String before = """
                public class CacheLoader {
                    void load(byte[] data) throws Exception {
                        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data));
                        in.readObject();
                    }
                }
                """;
        var change = new UnsafeDeserializationHintsRecipe().transform("CacheLoader.java", before);
        assertTrue(change.isPresent());
        assertTrue(change.get().after().contains("CWE-502"));
        assertTrue(change.get().relativePath().endsWith(".deserialization-refactor-hints.md"));
    }
}
