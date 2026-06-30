package com.upgrd.core.verify;

import com.upgrd.core.model.VerifyReport;
import com.upgrd.core.model.VerifyReport.WildflySmoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code mvn verify} against the migrated project and writes {@code verify-report.json}.
 */
public final class VerifyEngine {

    public VerifyResult verify(VerifyOptions options) throws Exception {
        Path output = options.outputDir();
        Path migratedPom = output.resolve("migrated/pom.xml");
        if (!Files.isRegularFile(migratedPom)) {
            throw new IllegalArgumentException("Migrated POM not found: " + migratedPom + " — run apply first");
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
        if (options.securityScan()) {
            command.add("-Psecurity-verify");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(migratedDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String log = new String(process.getInputStream().readAllBytes());
        Files.writeString(logFile, log);
        int exitCode = process.waitFor();
        String commandLine = String.join(" ", command);

        WildflySmoke wildflySection = null;
        boolean runWildfly = options.wildflySmoke() || options.wildflyDeploy() || options.wildflyHttp();
        if (runWildfly && exitCode == 0) {
            var checker = new WildFlySmokeChecker();
            var smoke = options.wildflyDeploy()
                    ? checker.checkDeployAndHttp(output, true, options.wildflyDeploy() || options.wildflyHttp())
                    : checker.checkDeployAndHttp(output, false, options.wildflyHttp());
            wildflySection = VerifyReportWriter.toWildflySmoke(smoke);
        }

        var report = new VerifyReportWriter().build(
                exitCode == 0, exitCode, options.securityScan(), commandLine, logFile, log, wildflySection);
        Path reportPath = new VerifyReportWriter().write(report, output);

        return new VerifyResult(exitCode == 0, exitCode, report, reportPath, log, wildflySection);
    }

    public record VerifyOptions(
            Path outputDir,
            boolean securityScan,
            boolean wildflySmoke,
            boolean wildflyDeploy,
            boolean wildflyHttp) {
    }

    public record VerifyResult(
            boolean passed,
            int exitCode,
            VerifyReport report,
            Path reportPath,
            String log,
            WildflySmoke wildflySmoke) {
    }
}
