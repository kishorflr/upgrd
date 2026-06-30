package com.upgrd.cli.command;

import com.upgrd.core.model.AnalyzeWorkspace;
import com.upgrd.core.ui.ReportServer;
import com.upgrd.core.ui.WorkspaceStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "run",
        description = "Serve the local audit dashboard (localhost only)")
public final class RunCommand implements Callable<Integer> {

    @Option(names = "--serve-ui", description = "Start the audit dashboard HTTP server")
    private boolean serveUi;

    @Option(names = "--output", defaultValue = "./upgrd-out", description = "Report directory to serve")
    private Path output;

    @Option(names = "--port", defaultValue = "8765", description = "Localhost port")
    private int port;

    @Option(names = "--source", description = "Legacy source root for UI-driven analyze")
    private Path source;

    @Option(names = "--war", description = "Production WAR for UI-driven analyze")
    private Path war;

    @Option(names = "--logs-dir", description = "Default logs archive directory for UI analyze")
    private Path logsDir;

    @Override
    public Integer call() throws Exception {
        if (!serveUi) {
            System.err.println("Specify --serve-ui to start the audit dashboard.");
            return 1;
        }
        Files.createDirectories(output);
        if (source != null && war != null) {
            new WorkspaceStore().save(output, new AnalyzeWorkspace(
                    source.toAbsolutePath().normalize().toString(),
                    war.toAbsolutePath().normalize().toString(),
                    output.toAbsolutePath().normalize().toString(),
                    logsDir != null ? logsDir.toAbsolutePath().normalize().toString() : null));
        }

        try (ReportServer server = new ReportServer(output, port)) {
            server.start();
            System.out.printf("UpGrd audit dashboard running at %s%n", server.baseUrl());
            System.out.println("  Coverage tab: configure workspace and run log analysis from the browser.");
            System.out.println("  Press Ctrl+C to stop.");
            Thread.currentThread().join();
        }
        return 0;
    }
}
