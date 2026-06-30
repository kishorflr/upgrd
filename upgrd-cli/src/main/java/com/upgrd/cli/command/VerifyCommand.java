package com.upgrd.cli.command;

import com.upgrd.core.failure.FailureReportService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "verify",
        description = "Run mvn verify on the migrated application (compile, test, package)")
public final class VerifyCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory containing migrated/")
    private Path output;

    @Option(names = "--anonymous-report", defaultValue = "true",
            description = "On failure, write a sanitized report under migrated/.upgrd/failure-report/")
    private boolean anonymousReport;

    @Option(names = "--security-scan", defaultValue = "false",
            description = "Also run SpotBugs + OWASP Dependency-Check (-Psecurity-verify)")
    private boolean securityScan;

    @Override
    public Integer call() throws Exception {
        Path migratedPom = output.resolve("migrated/pom.xml");
        if (!Files.isRegularFile(migratedPom)) {
            throw new IllegalArgumentException("Migrated POM not found: " + migratedPom
                    + " — run `upgrd apply` first");
        }

        Path migratedDir = output.resolve("migrated").toAbsolutePath().normalize();
        Path reportDir = migratedDir.resolve(".upgrd/failure-report");
        Files.createDirectories(reportDir);
        Path logFile = reportDir.resolve("last-run.log");

        List<String> command = new ArrayList<>();
        command.add("mvn");
        command.add("-f");
        command.add(migratedPom.toString());
        command.add("verify");
        if (securityScan) {
            command.add("-Psecurity-verify");
        }

        System.out.printf("UpGrd verify: running in %s%n", migratedDir);
        System.out.printf("  Command: %s%n", String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(migratedDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String log = new String(process.getInputStream().readAllBytes());
        Files.writeString(logFile, log);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println(log);
            System.out.println("  Verify: PASSED");
        } else {
            System.out.println(log);
            System.out.printf("  Verify: FAILED (exit %d)%n", exitCode);
            if (anonymousReport) {
                var written = new FailureReportService().generateFromText(
                        log, reportDir, "VERIFY_FAILURE", migratedDir);
                System.out.println("  Anonymous failure report (safe for external AI):");
                written.forEach(path -> System.out.printf("    %s%n", path));
            }
        }
        return exitCode;
    }
}
