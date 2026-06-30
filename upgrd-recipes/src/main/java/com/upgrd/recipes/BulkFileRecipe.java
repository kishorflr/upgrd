package com.upgrd.recipes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link ProjectAwareRecipe} that emits multiple file changes from the project tree.
 */
public interface BulkFileRecipe extends ProjectAwareRecipe {

    List<FileChange> generateChanges(Path projectRoot) throws IOException;
}
