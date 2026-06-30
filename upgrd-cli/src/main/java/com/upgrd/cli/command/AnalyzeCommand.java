package com.upgrd.cli.command;

import com.upgrd.core.AnalyzeEngine;
import com.upgrd.core.model.AnalysisInput;
import com.upgrd.core.model.AnalysisReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "analyze",
        description = "Analyze source, WAR, and logs; write analysis-report.json")
public final class AnalyzeCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "Java project source root")
    private Path source;

    @Option(names = "--war", required = true, description = "Deployed WAR file")
    private Path war;

    @Option(names = "--logs", split = ",", description = "Comma-separated log file paths")
    private List<Path> logs = new ArrayList<>();

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Output directory")
    private Path output;

    @Override
    public Integer call() throws Exception {
        AnalyzeEngine engine = new AnalyzeEngine();
        AnalysisReport report = engine.analyze(new AnalysisInput(source, war, logs, output));
        Path reportFile = engine.writeReport(report, output);

        System.out.printf("UpGrd analysis complete.%n");
        System.out.printf("  Build system: %s%n", report.discovery().buildSystem());
        System.out.printf("  WAR classes: %d | Source classes: %d%n",
                report.sync().warClassCount(), report.sync().sourceClassCount());
        System.out.printf("  Only in WAR: %d | Only in source: %d%n",
                report.sync().onlyInWar().size(), report.sync().onlyInSource().size());
        System.out.printf("  Log hits: %d | Unused WAR classes: %d%n",
                report.usage().totalHits(), report.usage().unusedInWar().size());
        System.out.printf("  Report: %s%n", reportFile.toAbsolutePath());
        return 0;
    }
}
