package com.upgrd.cli;

import com.upgrd.cli.command.AnalyzeCommand;
import com.upgrd.cli.command.ApplyCommand;
import com.upgrd.cli.command.ExportCommand;
import com.upgrd.cli.command.PlanCommand;
import com.upgrd.cli.command.ReportFailureCommand;
import com.upgrd.cli.command.pipeline.PipelineCommand;
import com.upgrd.cli.command.rewrite.RewriteCommand;
import com.upgrd.cli.command.weblogic.WebLogicCommand;
import com.upgrd.cli.command.wildfly.WildFlyCommand;
import com.upgrd.cli.command.RunCommand;
import com.upgrd.cli.command.VerifyCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "upgrd",
        mixinStandardHelpOptions = true,
        version = "UpGrd 1.3.0-SNAPSHOT",
        description = "Edge-local Java modernization toolkit (no AI at runtime)",
        subcommands = {
                AnalyzeCommand.class,
                PlanCommand.class,
                ApplyCommand.class,
                VerifyCommand.class,
                RunCommand.class,
                ReportFailureCommand.class,
                ExportCommand.class,
                WildFlyCommand.class,
                WebLogicCommand.class,
                RewriteCommand.class,
                PipelineCommand.class
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
