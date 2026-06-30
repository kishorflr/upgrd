package com.upgrd.recipes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs {@link FileRecipe} transforms against project sources on disk.
 */
public final class RecipeExecutor {

    private static final Set<String> JAVA_EXT = Set.of(".java");
    private static final Set<String> CONFIG_EXT = Set.of(".java", ".properties", ".xml");

    public RecipeRunResult run(FileRecipe recipe, Path sourceRoot) throws IOException {
        return run(recipe, sourceRoot, JAVA_EXT);
    }

    public RecipeRunResult runOnProject(FileRecipe recipe, Path projectRoot) throws IOException {
        return run(recipe, projectRoot, CONFIG_EXT);
    }

    private RecipeRunResult run(FileRecipe recipe, Path root, Set<String> extensions) throws IOException {
        List<Path> files = listFiles(root, extensions);
        if (files.isEmpty()) {
            return new RecipeRunResult(List.of(), 0, "No matching sources under " + root);
        }

        List<FileRecipe.FileChange> changes = new ArrayList<>();
        for (Path file : files) {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            String relative = root.relativize(file).toString().replace('\\', '/');
            recipe.transform(relative, before).ifPresent(change -> {
                try {
                    Path target = change.relativePath().equals(relative)
                            ? file
                            : root.resolve(change.relativePath());
                    Files.createDirectories(target.getParent());
                    Files.writeString(target, change.after(), StandardCharsets.UTF_8);
                    changes.add(change);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to write " + file, ex);
                }
            });
        }

        return new RecipeRunResult(changes, changes.size(),
                "Applied " + recipe.displayName() + " to " + changes.size() + " file(s)");
    }

    private List<Path> listFiles(Path root, Set<String> extensions) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> extensions.stream().anyMatch(ext -> p.toString().endsWith(ext)))
                    .sorted()
                    .toList();
        }
    }

    public record RecipeRunResult(List<FileRecipe.FileChange> changes, int changedFiles, String message) {
    }
}
