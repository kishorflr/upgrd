package com.upgrd.cli.command;

import com.upgrd.core.export.AuditExporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "export",
        description = "Bundle audit reports into JSON, Markdown, HTML, and optional PDF")
public final class ExportCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Option(names = "--html", defaultValue = "true", description = "Generate print-friendly audit-export.html")
    private boolean html;

    @Option(names = "--pdf", defaultValue = "false", description = "Generate audit-export.pdf compliance summary")
    private boolean pdf;

    @Override
    public Integer call() throws Exception {
        var options = new AuditExporter.ExportOptions(html, pdf);
        var result = new AuditExporter().export(output, options);
        System.out.println("UpGrd audit export:");
        System.out.printf("  %s (%d reports)%n", result.jsonFile(), result.reportCount());
        System.out.printf("  %s%n", result.markdownFile());
        if (result.htmlFile() != null) {
            System.out.printf("  %s (print to PDF from browser)%n", result.htmlFile());
        }
        if (result.pdfFile() != null) {
            System.out.printf("  %s%n", result.pdfFile());
        }
        return 0;
    }
}
