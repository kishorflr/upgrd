package com.upgrd.cli.command.weblogic;

import com.upgrd.core.weblogic.WebLogicCliService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "status", description = "Show WebLogic deploy scaffold and WAR build status")
public final class WebLogicStatusCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        var status = new WebLogicCliService().status(output);
        System.out.println("UpGrd WebLogic status:");
        status.notes().forEach(note -> System.out.println("  " + note));
        return status.scaffoldPresent() ? 0 : 1;
    }
}
