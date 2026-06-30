package com.upgrd.cli.command.rewrite;

import com.upgrd.core.openrewrite.OpenRewriteRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "run",
        description = "Run OpenRewrite AST migrations on the migrated project")
public final class RewriteRunCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Option(names = "--dry-run", defaultValue = "false", description = "Preview changes without modifying sources")
    private boolean dryRun;

    @Option(names = "--require-dry-run", defaultValue = "true",
            description = "When applying, require .upgrd/rewrite/dry-run-passed from apply or prior dry-run")
    private boolean requireDryRun;

    @Option(names = "--force", defaultValue = "false", description = "Skip dry-run gate check")
    private boolean force;

    @Override
    public Integer call() throws Exception {
        var result = new OpenRewriteRunner().run(output, dryRun, requireDryRun && !force && !dryRun);
        System.out.println(result.message());
        if (!result.log().isBlank()) {
            System.out.println(result.log());
        }
        System.out.printf("Log: %s/migrated/.upgrd/rewrite/last-run.log%n", output.toAbsolutePath().normalize());
        return result.success() ? 0 : result.exitCode();
    }
}
