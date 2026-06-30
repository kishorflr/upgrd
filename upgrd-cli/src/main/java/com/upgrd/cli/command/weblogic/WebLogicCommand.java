package com.upgrd.cli.command.weblogic;

import picocli.CommandLine.Command;

@Command(
        name = "weblogic",
        description = "Validate WebLogic production deploy scaffold (no local container)",
        subcommands = {
                WebLogicStatusCommand.class,
                WebLogicValidateCommand.class
        },
        mixinStandardHelpOptions = true)
public final class WebLogicCommand {
}
