package com.upgrd.core.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.VerifyReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class VerifyReportWriter {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Path write(VerifyReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path file = outputDir.resolve("verify-report.json");
        mapper.writeValue(file.toFile(), report);
        return file;
    }

    public VerifyReport build(
            boolean passed,
            int exitCode,
            boolean securityScan,
            String command,
            Path logFile,
            String logText) {
        return new VerifyReport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                passed,
                exitCode,
                securityScan,
                command,
                logFile == null ? null : logFile.toString(),
                summarize(logText, passed, securityScan));
    }

    private List<String> summarize(String logText, boolean passed, boolean securityScan) {
        List<String> lines = new ArrayList<>();
        if (passed) {
            lines.add("Maven verify completed successfully");
        } else {
            lines.add("Maven verify failed — see log and anonymous failure report");
        }
        if (securityScan) {
            lines.add("Security scan profile (-Psecurity-verify) was enabled");
        }
        if (logText != null) {
            if (logText.contains("BUILD FAILURE")) {
                lines.add("Build failure detected in Maven output");
            }
            if (logText.contains("Tests run:")) {
                logText.lines()
                        .filter(l -> l.contains("Tests run:") || l.contains("[ERROR]"))
                        .limit(5)
                        .forEach(lines::add);
            }
        }
        return List.copyOf(lines);
    }
}
