package com.upgrd.core.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.upgrd.core.model.ApprovedPlan;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves the local audit dashboard and JSON reports from the output directory.
 * Edge-only: binds to localhost, no external network.
 */
public final class ReportServer implements AutoCloseable {

    private static final Set<String> REPORT_FILES = Set.of(
            "analysis-report.json",
            "upgrade-plan.json",
            "approved-plan.json",
            "change-ledger.json",
            "design-advisory.json",
            "apply-report.json",
            "sync-report.json",
            "usage-report.json",
            "security-report.json",
            "app-documentation.json",
            "verify-report.json",
            "audit-export.json",
            "anti-pattern-report.json",
            "upgrade-preview-report.json",
            "change-ledger-preview.json",
            "api-compatibility-report.json");

    private final HttpServer server;
    private final Path outputDir;
    private final ClassLoader uiClassLoader;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public ReportServer(Path outputDir, int port) throws IOException {
        this.outputDir = outputDir.toAbsolutePath().normalize();
        this.uiClassLoader = ReportServer.class.getClassLoader();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/reports/", this::handleReport);
        server.createContext("/api/approval", this::handleApproval);
        server.createContext("/ui/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(2));
    }

    public void start() {
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        serveResource(exchange, "ui/index.html", "text/html; charset=utf-8");
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String path = exchange.getRequestURI().getPath().substring("/ui/".length());
        String contentType = path.endsWith(".css") ? "text/css; charset=utf-8"
                : path.endsWith(".js") ? "application/javascript; charset=utf-8"
                : "text/plain; charset=utf-8";
        serveResource(exchange, "ui/" + path, contentType);
    }

    private void handleApproval(HttpExchange exchange) throws IOException {
        addCors(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            serveOutputFile(exchange, "approved-plan.json");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            try {
                ApprovedPlan approval = mapper.readValue(body, ApprovedPlan.class);
                if (approval.steps() == null || approval.steps().isEmpty()) {
                    sendJsonError(exchange, 400, "Approval must include at least one step");
                    return;
                }
                Files.createDirectories(outputDir);
                mapper.writeValue(outputDir.resolve("approved-plan.json").toFile(), approval);
                byte[] response = "{\"saved\":true}".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            } catch (Exception ex) {
                sendJsonError(exchange, 400, "Invalid approval payload: " + ex.getMessage());
            }
            return;
        }
        exchange.sendResponseHeaders(405, -1);
    }

    private void handleReport(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String name = exchange.getRequestURI().getPath().substring("/api/reports/".length());
        if (!REPORT_FILES.contains(name) || name.contains("..")) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        serveOutputFile(exchange, name);
    }

    private void serveOutputFile(HttpExchange exchange, String name) throws IOException {
        Path file = outputDir.resolve(name);
        if (!Files.isRegularFile(file)) {
            sendJsonError(exchange, 404, "not found: " + name);
            return;
        }
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        addCors(exchange);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void sendJsonError(HttpExchange exchange, int code, String message) throws IOException {
        byte[] body = ("{\"error\":\"" + message.replace("\"", "'") + "\"}").getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        addCors(exchange);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void serveResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream in = uiClassLoader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
