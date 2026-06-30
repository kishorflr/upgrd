package com.upgrd.cli;

import com.upgrd.cli.command.AnalyzeCommand;
import com.upgrd.cli.command.ApplyCommand;
import com.upgrd.cli.command.PlanCommand;
import com.upgrd.cli.command.ReportFailureCommand;
import com.upgrd.cli.command.RunCommand;
import com.upgrd.cli.command.VerifyCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "upgrd",
        mixinStandardHelpOptions = true,
        version = "UpGrd 1.1.0-SNAPSHOT",
        description = "Edge-local Java modernization toolkit (no AI at runtime)",
        subcommands = {
                AnalyzeCommand.class,
                PlanCommand.class,
                ApplyCommand.class,
                VerifyCommand.class,
                RunCommand.class,
                ReportFailureCommand.class
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
