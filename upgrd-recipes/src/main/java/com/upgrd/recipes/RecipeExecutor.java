package com.upgrd.recipes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs {@link FileRecipe} transforms against Java sources on disk.
 */
public final class RecipeExecutor {

    public RecipeRunResult run(FileRecipe recipe, Path sourceRoot) throws IOException {
        List<Path> javaFiles = listJavaFiles(sourceRoot);
        if (javaFiles.isEmpty()) {
            return new RecipeRunResult(List.of(), 0, "No Java sources found under " + sourceRoot);
        }

        List<FileRecipe.FileChange> changes = new ArrayList<>();
        for (Path file : javaFiles) {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
            recipe.transform(relative, before).ifPresent(change -> {
                try {
                    Files.writeString(file, change.after(), StandardCharsets.UTF_8);
                    changes.add(change);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to write " + file, ex);
                }
            });
        }

        return new RecipeRunResult(changes, changes.size(),
                "Applied " + recipe.displayName() + " to " + changes.size() + " file(s)");
    }

    private List<Path> listJavaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    public record RecipeRunResult(List<FileRecipe.FileChange> changes, int changedFiles, String message) {
    }
}
