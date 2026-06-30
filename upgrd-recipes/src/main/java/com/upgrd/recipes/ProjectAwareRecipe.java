package com.upgrd.recipes;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link FileRecipe} that can inspect the project tree before per-file transforms.
 */
public interface ProjectAwareRecipe extends FileRecipe {

    void prepare(Path projectRoot) throws IOException;
}
