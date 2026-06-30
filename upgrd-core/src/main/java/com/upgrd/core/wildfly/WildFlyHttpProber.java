package com.upgrd.core.wildfly;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Probes a deployed WildFly app over HTTP (live smoke after WAR deploy).
 */
public final class WildFlyHttpProber {

    public static final int DEFAULT_PORT = 8080;
    private static final Pattern CONTEXT_ROOT = Pattern.compile(
            "<context-root>([^<]+)</context-root>", Pattern.CASE_INSENSITIVE);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HttpProbeResult probe(Path outputDir, int port, int maxAttempts, long delayMs)
            throws IOException, InterruptedException {
        Path migrated = outputDir.resolve("migrated").toAbsolutePath().normalize();
        String context = readContextRoot(migrated);
        String url = "http://localhost:" + port + "/" + context + "/";

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpProbeResult result = probeOnce(url);
            if (result.reachable()) {
                return result;
            }
            if (attempt < maxAttempts) {
                Thread.sleep(delayMs);
            }
        }
        return probeOnce(url);
    }

    public HttpProbeResult probeOnce(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            boolean ok = code >= 200 && code < 500;
            return new HttpProbeResult(true, ok, code, url,
                    ok ? "HTTP " + code + " from " + url : "HTTP " + code + " (unexpected) from " + url);
        } catch (Exception ex) {
            return new HttpProbeResult(false, false, 0, url, "HTTP probe failed: " + ex.getMessage());
        }
    }

    public String readContextRoot(Path migratedRoot) throws IOException {
        Path jbossWeb = migratedRoot.resolve("deploy/wildfly/jboss-web.xml");
        if (Files.isRegularFile(jbossWeb)) {
            Matcher matcher = CONTEXT_ROOT.matcher(Files.readString(jbossWeb));
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "app-web";
    }

    public record HttpProbeResult(
            boolean checked,
            boolean reachable,
            int statusCode,
            String url,
            String message) {
    }
}
