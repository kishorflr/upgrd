package com.upgrd.cli.command.rewrite;

import picocli.CommandLine.Command;

@Command(
        name = "rewrite",
        description = "Run OpenRewrite AST migrations (complements UpGrd file recipes)",
        subcommands = {RewriteRunCommand.class},
        mixinStandardHelpOptions = true)
public final class RewriteCommand {
}
