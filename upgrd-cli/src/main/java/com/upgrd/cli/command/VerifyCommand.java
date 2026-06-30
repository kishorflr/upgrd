package com.upgrd.cli.command;

import com.upgrd.core.failure.FailureReportService;
import com.upgrd.core.model.VerifyReport.WildflySmoke;
import com.upgrd.core.verify.VerifyReportWriter;
import com.upgrd.core.verify.WildFlySmokeChecker;
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

    @Option(names = "--wildfly-smoke", defaultValue = "false",
            description = "After verify, check WildFly deploy scaffold and Docker status")
    private boolean wildflySmoke;

    @Option(names = "--wildfly-deploy", defaultValue = "false",
            description = "After successful verify, build and stage WAR for WildFly (implies --wildfly-smoke)")
    private boolean wildflyDeploy;

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
        String commandLine = String.join(" ", command);

        WildflySmoke wildflySection = null;
        boolean runWildfly = wildflySmoke || wildflyDeploy;
        if (runWildfly && exitCode == 0) {
            var checker = new WildFlySmokeChecker();
            var smoke = wildflyDeploy
                    ? checker.checkAndDeploy(output, true)
                    : checker.check(output);
            wildflySection = VerifyReportWriter.toWildflySmoke(smoke);
            System.out.println("  WildFly smoke check:");
            smoke.notes().forEach(note -> System.out.printf("    %s%n", note));
        } else if (runWildfly) {
            System.out.println("  WildFly smoke check: skipped (verify failed)");
        }

        var verifyReport = new VerifyReportWriter().build(
                exitCode == 0, exitCode, securityScan, commandLine, logFile, log, wildflySection);
        Path verifyReportPath = new VerifyReportWriter().write(verifyReport, output);

        if (exitCode == 0) {
            System.out.println(log);
            System.out.println("  Verify: PASSED");
            System.out.printf("  Report: %s%n", verifyReportPath);
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
