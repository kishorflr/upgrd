package com.upgrd.core.compat;

import com.upgrd.core.model.ApiRemediationType;

record ApiCatalogEntry(
        String id,
        String apiPattern,
        String category,
        String description,
        String replacement,
        ApiRemediationType remediationType,
        String recipeId,
        String planStepId) {
}
