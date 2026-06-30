package com.upgrd.recipes;

import com.upgrd.recipes.api.JavaxToJakartaRecipe;
import com.upgrd.recipes.framework.StrutsActionToSpringControllerRecipe;
import com.upgrd.recipes.logging.Log4j1ToSlf4jRecipe;
import com.upgrd.recipes.security.ExternalizeSecretsRecipe;
import com.upgrd.recipes.security.RemediateWeakHashRecipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RecipeRegistry {

    private final Map<String, FileRecipe> byCoordinate = new LinkedHashMap<>();

    public RecipeRegistry() {
        register(new Log4j1ToSlf4jRecipe());
        register(new StrutsActionToSpringControllerRecipe());
        register(new JavaxToJakartaRecipe());
        register(new RemediateWeakHashRecipe());
        register(new ExternalizeSecretsRecipe());
    }

    public Optional<FileRecipe> resolve(String coordinate) {
        return Optional.ofNullable(byCoordinate.get(coordinate));
    }

    private void register(FileRecipe recipe) {
        byCoordinate.put(recipe.coordinate(), recipe);
    }
}
