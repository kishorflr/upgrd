package com.upgrd.cli.command.pipeline;

import picocli.CommandLine.Command;

@Command(
        name = "pipeline",
        description = "Run end-to-end modernization pipeline",
        subcommands = {PipelineRunCommand.class},
        mixinStandardHelpOptions = true)
public final class PipelineCommand {
}
