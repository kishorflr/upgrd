package com.upgrd.cli.command.weblogic;

import com.upgrd.core.weblogic.WebLogicCliService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "validate", description = "Validate WebLogic overlay files and deploy.sh references")
public final class WebLogicValidateCommand implements Callable<Integer> {

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "UpGrd output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        var result = new WebLogicCliService().validate(output);
        System.out.println(result.message());
        return result.success() ? 0 : result.exitCode();
    }
}
