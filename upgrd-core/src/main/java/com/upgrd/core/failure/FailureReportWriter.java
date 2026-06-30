package com.upgrd.core.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.model.AnonymousFailureReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes anonymous failure reports for local review and external AI sharing.
 */
public final class FailureReportWriter {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public List<String> write(AnonymousFailureReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path json = outputDir.resolve("anonymous-failure-report.json");
        Path markdown = outputDir.resolve("anonymous-failure-report.md");
        mapper.writeValue(json.toFile(), report);
        Files.writeString(markdown, report.aiPromptMarkdown());
        return List.of(json.toString(), markdown.toString());
    }
}
