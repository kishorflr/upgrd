package com.upgrd.recipes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeCatalogTest {

    @Test
    void resolvesKnownStepIds() {
        RecipeCatalog catalog = new RecipeCatalog();

        assertTrue(catalog.findByStepId("upgrade-java").isPresent());
        assertTrue(catalog.findByStepId("convert-maven").isPresent());
        assertFalse(catalog.findByStepId("missing-step").isPresent());
    }
}
