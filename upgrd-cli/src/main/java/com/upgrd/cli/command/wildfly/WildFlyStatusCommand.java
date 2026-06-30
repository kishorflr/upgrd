package com.upgrd.cli.command.wildfly;

import com.upgrd.core.wildfly.WildFlyCliService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "status",
        description = "Show WildFly scaffold, Docker, and deployment status")
public final class WildFlyStatusCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        var status = new WildFlyCliService().status(output);
        System.out.println("UpGrd WildFly status:");
        status.notes().forEach(note -> System.out.println("  " + note));
        return status.scaffoldPresent() ? 0 : 1;
    }
}
