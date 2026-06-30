package com.upgrd.cli.command;

import com.upgrd.core.failure.FailureReportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "report-failure",
        description = "Generate an anonymous, AI-shareable failure report from build/test logs")
public final class ReportFailureCommand implements Callable<Integer> {

    @Option(names = "--log", description = "Path to captured Maven/test log file", required = true)
    private Path logFile;

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Option(names = "--kind", defaultValue = "VERIFY_FAILURE", description = "Failure kind label for the report")
    private String failureKind;

    @Override
    public Integer call() throws Exception {
        if (!Files.isRegularFile(logFile)) {
            throw new IllegalArgumentException("Log file not found: " + logFile);
        }

        Path migratedRoot = output.resolve("migrated");
        Path reportDir = migratedRoot.resolve(".upgrd/failure-report");
        var service = new FailureReportService();
        var written = service.generateFromLog(logFile, reportDir, failureKind, migratedRoot);

        System.out.println("UpGrd anonymous failure report:");
        written.forEach(path -> System.out.printf("  %s%n", path));
        System.out.println();
        System.out.println("Share anonymous-failure-report.md with an external AI assistant.");
        System.out.println("Business logic, paths, and secrets are redacted or tokenized.");
        return 0;
    }
}
