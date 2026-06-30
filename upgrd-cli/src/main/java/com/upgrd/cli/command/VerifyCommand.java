package com.upgrd.cli.command;

import com.upgrd.core.failure.FailureReportService;
import com.upgrd.core.verify.VerifyEngine;
import com.upgrd.core.verify.VerifyEngine.VerifyOptions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
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

    @Option(names = "--wildfly-http", defaultValue = "false",
            description = "After deploy/smoke, probe http://localhost:8080/{context}/ (requires running container)")
    private boolean wildflyHttp;

    @Override
    public Integer call() throws Exception {
        System.out.printf("UpGrd verify: output %s%n", output.toAbsolutePath());

        var result = new VerifyEngine().verify(new VerifyOptions(
                output, securityScan, wildflySmoke, wildflyDeploy, wildflyHttp));

        if (result.wildflySmoke() != null) {
            System.out.println("  WildFly smoke check:");
            result.wildflySmoke().notes().forEach(note -> System.out.printf("    %s%n", note));
        }

        if (result.passed()) {
            System.out.println(result.log());
            System.out.println("  Verify: PASSED");
            System.out.printf("  Report: %s%n", result.reportPath());
        } else {
            System.out.println(result.log());
            System.out.printf("  Verify: FAILED (exit %d)%n", result.exitCode());
            if (anonymousReport) {
                var migratedDir = output.resolve("migrated").toAbsolutePath().normalize();
                var reportDir = migratedDir.resolve(".upgrd/failure-report");
                var written = new FailureReportService().generateFromText(
                        result.log(), reportDir, "VERIFY_FAILURE", migratedDir);
                System.out.println("  Anonymous failure report (safe for external AI):");
                written.forEach(path -> System.out.printf("    %s%n", path));
            }
        }

        return result.passed() ? 0 : result.exitCode();
    }
}
