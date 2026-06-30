package com.upgrd.recipes.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RemediateWeakHashRecipeTest {

    @Test
    void replacesMd5() {
        String before = "MessageDigest.getInstance(\"MD5\");";
        var change = new RemediateWeakHashRecipe().transform("HashUtil.java", before);
        assertTrue(change.isPresent());
        assertTrue(change.get().after().contains("SHA-256"));
    }
}
