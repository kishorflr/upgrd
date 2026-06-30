package com.upgrd.recipes;

import com.upgrd.recipes.api.JavaxToJakartaRecipe;
import com.upgrd.recipes.collections.ReplaceRawCollectionsRecipe;
import com.upgrd.recipes.framework.Spring4To6Recipe;
import com.upgrd.recipes.framework.StrutsActionToSpringControllerRecipe;
import com.upgrd.recipes.framework.StrutsConfigToSpringRecipe;
import com.upgrd.recipes.framework.StrutsFormBeanScaffoldRecipe;
import com.upgrd.recipes.framework.StrutsJspToThymeleafRecipe;
import com.upgrd.recipes.framework.StrutsViewValidationHintsRecipe;
import com.upgrd.recipes.language.UpgradeJavaRecipe;
import com.upgrd.recipes.logging.Log4j1ToSlf4jRecipe;
import com.upgrd.recipes.security.ExternalizeSecretsRecipe;
import com.upgrd.recipes.security.RemediateWeakHashRecipe;
import com.upgrd.recipes.security.SqlConcatenationHintsRecipe;
import com.upgrd.recipes.security.UnsafeDeserializationHintsRecipe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class RecipeRegistry {

    private final Map<String, FileRecipe> byCoordinate = new LinkedHashMap<>();

    public RecipeRegistry() {
        register(new Log4j1ToSlf4jRecipe());
        register(new StrutsFormBeanScaffoldRecipe());
        register(new StrutsActionToSpringControllerRecipe());
        register(new StrutsConfigToSpringRecipe());
        register(new StrutsViewValidationHintsRecipe());
        register(new StrutsJspToThymeleafRecipe());
        register(new Spring4To6Recipe());
        register(new JavaxToJakartaRecipe());
        register(new UpgradeJavaRecipe());
        register(new ReplaceRawCollectionsRecipe());
        register(new RemediateWeakHashRecipe());
        register(new ExternalizeSecretsRecipe());
        register(new SqlConcatenationHintsRecipe());
        register(new UnsafeDeserializationHintsRecipe());
    }

    public Optional<FileRecipe> resolve(String coordinate) {
        return Optional.ofNullable(byCoordinate.get(coordinate));
    }

    private void register(FileRecipe recipe) {
        byCoordinate.put(recipe.coordinate(), recipe);
    }
}
