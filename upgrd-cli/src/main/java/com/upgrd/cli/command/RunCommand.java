package com.upgrd.cli.command;

import com.upgrd.core.ui.ReportServer;
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

    @Override
    public Integer call() throws Exception {
        if (!serveUi) {
            System.err.println("Specify --serve-ui to start the audit dashboard.");
            return 1;
        }
        if (!Files.isDirectory(output)) {
            throw new IllegalArgumentException("Output directory not found: " + output);
        }

        try (ReportServer server = new ReportServer(output, port)) {
            server.start();
            System.out.printf("UpGrd audit dashboard running at %s%n", server.baseUrl());
            System.out.println("  Press Ctrl+C to stop.");
            Thread.currentThread().join();
        }
        return 0;
    }
}
