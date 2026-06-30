package com.upgrd.recipes;

public record RecipeDefinition(
        String stepId,
        String openRewriteRecipe,
        String description,
        boolean implemented) {
}
