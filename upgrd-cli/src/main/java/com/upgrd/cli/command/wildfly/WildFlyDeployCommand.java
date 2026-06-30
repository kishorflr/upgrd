package com.upgrd.cli.command.wildfly;

import com.upgrd.core.wildfly.WildFlyCliService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "deploy",
        description = "Build and deploy WAR to local WildFly")
public final class WildFlyDeployCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Option(names = "--build", defaultValue = "true", description = "Run mvn -Plocal-wildfly package before deploy")
    private boolean build;

    @Override
    public Integer call() throws Exception {
        var result = new WildFlyCliService().deploy(output, build);
        System.out.println(result.message());
        return result.success() ? 0 : result.exitCode();
    }
}
