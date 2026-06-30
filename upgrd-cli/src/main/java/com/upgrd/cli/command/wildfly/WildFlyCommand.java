package com.upgrd.cli.command.wildfly;

import picocli.CommandLine.Command;

@Command(
        name = "wildfly",
        description = "Manage local WildFly deployment (Docker + WAR)",
        subcommands = {
                WildFlyStartCommand.class,
                WildFlyStopCommand.class,
                WildFlyDeployCommand.class,
                WildFlyUndeployCommand.class,
                WildFlyStatusCommand.class
        },
        mixinStandardHelpOptions = true)
public final class WildFlyCommand {
}
