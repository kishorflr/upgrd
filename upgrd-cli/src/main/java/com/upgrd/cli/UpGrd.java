package com.upgrd.cli;

import com.upgrd.cli.command.AnalyzeCommand;
import com.upgrd.cli.command.PlanCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "upgrd",
        mixinStandardHelpOptions = true,
        version = "UpGrd 1.0.0",
        description = "Edge-local Java modernization toolkit (no AI at runtime)",
        subcommands = {
                AnalyzeCommand.class,
                PlanCommand.class
        })
public final class UpGrd implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new UpGrd()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
