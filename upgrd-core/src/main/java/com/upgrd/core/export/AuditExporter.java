package com.upgrd.core.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AuditExport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundles all UpGrd JSON reports into a single audit export for sign-off.
 */
public final class AuditExporter {

    private static final List<String> REPORT_FILES = List.of(
            "analysis-report.json",
            "upgrade-plan.json",
            "change-ledger.json",
            "design-advisory.json",
            "apply-report.json",
            "verify-report.json",
            "sync-report.json",
            "usage-report.json",
            "security-report.json",
            "anti-pattern-report.json",
            "api-compatibility-report.json",
            "feature-usage-report.json",
            "log-source-manifest.json",
            "war-context.json",
            "war-merge-report.json",
            "app-documentation.json");

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public ExportResult export(Path outputDir, ExportOptions options) throws IOException {
        Files.createDirectories(outputDir);
        Map<String, Object> reports = new LinkedHashMap<>();
        List<String> included = new java.util.ArrayList<>();

        for (String name : REPORT_FILES) {
            Path file = outputDir.resolve(name);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Object parsed = mapper.readValue(file.toFile(), new TypeReference<Map<String, Object>>() {});
            String key = reportKey(name);
            reports.put(key, parsed);
            included.add(name);
        }

        AuditExport bundle = new AuditExport(
                AnalyzeEngine.VERSION,
                Instant.now(),
                outputDir.toAbsolutePath().normalize().toString(),
                List.copyOf(included),
                Map.copyOf(reports));

        Path json = outputDir.resolve("audit-export.json");
        Path markdown = outputDir.resolve("audit-export.md");
        mapper.writeValue(json.toFile(), bundle);
        Files.writeString(markdown, renderMarkdown(bundle));

        Path html = null;
        Path pdf = null;
        if (options.includeHtml()) {
            html = new AuditHtmlExporter().export(bundle, outputDir);
        }
        if (options.includePdf()) {
            pdf = new AuditPdfExporter().export(bundle, outputDir);
        }

        return new ExportResult(json, markdown, html, pdf, included.size());
    }

    public ExportResult export(Path outputDir) throws IOException {
        return export(outputDir, ExportOptions.defaults());
    }

    private String reportKey(String fileName) {
        return fileName.replace(".json", "").replace("-", "_");
    }

    private String renderMarkdown(AuditExport bundle) {
        StringBuilder md = new StringBuilder();
        md.append("# UpGrd Audit Export\n\n");
        md.append("- **Generated:** ").append(bundle.exportedAt()).append("\n");
        md.append("- **UpGrd version:** ").append(bundle.upgrdVersion()).append("\n");
        md.append("- **Output directory:** `").append(bundle.outputDirectory()).append("`\n");
        md.append("- **Reports included:** ").append(bundle.includedReports().size()).append("\n\n");
        md.append("## Included files\n\n");
        bundle.includedReports().forEach(name -> md.append("- `").append(name).append("`\n"));
        md.append("\n## Summary\n\n");
        md.append("This bundle consolidates edge-local modernization audit data. ");
        md.append("Open `audit-export.json` for the full structured export or use individual report files in the dashboard.\n");
        return md.toString();
    }

    public record ExportResult(Path jsonFile, Path markdownFile, Path htmlFile, Path pdfFile, int reportCount) {
    }

    public record ExportOptions(boolean includeHtml, boolean includePdf) {
        public static ExportOptions defaults() {
            return new ExportOptions(true, false);
        }
    }
}
