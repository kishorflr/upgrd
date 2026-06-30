package com.upgrd.core.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.model.AnalyzeWorkspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceStore {

    public static final String WORKSPACE_FILE = ".upgrd/workspace.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public AnalyzeWorkspace load(Path outputDir) throws IOException {
        Path file = outputDir.resolve(WORKSPACE_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return mapper.readValue(file.toFile(), AnalyzeWorkspace.class);
    }

    public void save(Path outputDir, AnalyzeWorkspace workspace) throws IOException {
        Files.createDirectories(outputDir.resolve(".upgrd"));
        mapper.writeValue(outputDir.resolve(WORKSPACE_FILE).toFile(), workspace);
    }
}
