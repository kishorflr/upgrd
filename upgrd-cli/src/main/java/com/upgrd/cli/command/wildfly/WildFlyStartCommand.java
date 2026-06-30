package com.upgrd.cli.command.wildfly;

import com.upgrd.core.wildfly.WildFlyCliService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "start",
        description = "Start local WildFly via Docker Compose")
public final class WildFlyStartCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        var result = new WildFlyCliService().start(output);
        System.out.println(result.message());
        return result.exitCode();
    }
}
