package com.upgrd.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "verify",
        description = "Run automated tests in the migrated application (mvn test)")
public final class VerifyCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory containing migrated/")
    private Path output;

    @Override
    public Integer call() throws Exception {
        Path migratedPom = output.resolve("migrated/pom.xml");
        if (!Files.isRegularFile(migratedPom)) {
            throw new IllegalArgumentException("Migrated POM not found: " + migratedPom
                    + " — run `upgrd apply` first");
        }

        Path migratedDir = output.resolve("migrated").toAbsolutePath().normalize();
        System.out.printf("UpGrd verify: running tests in %s%n", migratedDir);
        System.out.printf("  Command: mvn -f %s test%n", migratedPom.toAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder("mvn", "-f", migratedPom.toString(), "test");
        builder.directory(migratedDir.toFile());
        builder.inheritIO();
        int exitCode = builder.start().waitFor();

        if (exitCode == 0) {
            System.out.println("  Verify: PASSED");
        } else {
            System.out.printf("  Verify: FAILED (exit %d)%n", exitCode);
        }
        return exitCode;
    }
}
