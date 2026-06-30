package com.upgrd.cli.command;

import com.upgrd.core.export.AuditExporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "export",
        description = "Bundle all audit JSON reports into audit-export.json for sign-off")
public final class ExportCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        var result = new AuditExporter().export(output);
        System.out.println("UpGrd audit export:");
        System.out.printf("  %s (%d reports)%n", result.jsonFile(), result.reportCount());
        System.out.printf("  %s%n", result.markdownFile());
        return 0;
    }
}
