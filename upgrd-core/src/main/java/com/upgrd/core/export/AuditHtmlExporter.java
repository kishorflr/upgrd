package com.upgrd.core.export;

import com.upgrd.core.model.AuditExport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates a print-friendly HTML audit report (browser Print → PDF).
 */
public final class AuditHtmlExporter {

    public Path export(AuditExport bundle, Path outputDir) throws IOException {
        Path html = outputDir.resolve("audit-export.html");
        Files.writeString(html, render(bundle));
        return html;
    }

    private String render(AuditExport bundle) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <title>UpGrd Audit Export</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; color: #111; }
                    h1 { border-bottom: 2px solid #333; padding-bottom: 0.5rem; }
                    h2 { margin-top: 2rem; color: #333; }
                    .meta { color: #555; }
                    pre { background: #f4f4f4; padding: 1rem; overflow-x: auto; font-size: 0.85rem; }
                    @media print { body { margin: 1cm; } pre { white-space: pre-wrap; } }
                  </style>
                </head>
                <body>
                  <h1>UpGrd Audit Export</h1>
                """);
        html.append("<p class=\"meta\">Generated: ").append(bundle.exportedAt()).append("<br>");
        html.append("UpGrd version: ").append(bundle.upgrdVersion()).append("<br>");
        html.append("Reports: ").append(bundle.includedReports().size()).append("</p>");

        for (String name : bundle.includedReports()) {
            Object report = bundle.reports().get(name.replace(".json", "").replace("-", "_"));
            html.append("<h2>").append(name).append("</h2>");
            html.append("<pre>").append(escape(String.valueOf(report))).append("</pre>");
        }

        html.append("""
                  <p class="meta">Edge-local audit — no cloud upload. Print this page to save as PDF.</p>
                </body>
                </html>
                """);
        return html.toString();
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
