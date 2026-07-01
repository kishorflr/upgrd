package com.upgrd.core.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Serves slices of large JSON reports for the audit dashboard (pagination + filters).
 */
public final class PaginatedReportService {

    static final Set<String> PAGINATED_REPORTS = Set.of(
            "change-ledger.json",
            "change-ledger-preview.json",
            "api-compatibility-report.json",
            "feature-usage-report.json",
            "log-source-manifest.json");

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public PaginatedReportPage page(Path outputDir, String reportName, int offset, int limit, String filter)
            throws IOException {
        if (!PAGINATED_REPORTS.contains(reportName) || reportName.contains("..")) {
            throw new IOException("Report does not support pagination: " + reportName);
        }
        Path file = outputDir.resolve(reportName);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Report not found: " + reportName);
        }

        int safeLimit = Math.clamp(limit <= 0 ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
        int safeOffset = Math.max(0, offset);

        JsonNode root = mapper.readTree(file.toFile());
        String arrayField = arrayFieldFor(reportName);
        JsonNode arrayNode = root.get(arrayField);
        if (arrayNode == null || !arrayNode.isArray()) {
            return new PaginatedReportPage(reportName, 0, safeOffset, safeLimit, summaryWithoutArray(root, arrayField), List.of());
        }

        List<JsonNode> filtered = filterItems(reportName, arrayNode, filter);
        int total = filtered.size();
        int from = Math.min(safeOffset, total);
        int to = Math.min(from + safeLimit, total);
        List<JsonNode> page = filtered.subList(from, to);

        return new PaginatedReportPage(
                reportName,
                total,
                from,
                safeLimit,
                summaryWithoutArray(root, arrayField),
                List.copyOf(page));
    }

    private String arrayFieldFor(String reportName) {
        return switch (reportName) {
            case "change-ledger.json", "change-ledger-preview.json" -> "changes";
            case "api-compatibility-report.json" -> "hits";
            case "feature-usage-report.json" -> "features";
            case "log-source-manifest.json" -> "entries";
            default -> throw new IllegalArgumentException("Unknown report: " + reportName);
        };
    }

    private List<JsonNode> filterItems(String reportName, JsonNode arrayNode, String filter) {
        List<JsonNode> items = new ArrayList<>();
        Iterator<JsonNode> it = arrayNode.elements();
        while (it.hasNext()) {
            JsonNode item = it.next();
            if (matchesFilter(reportName, item, filter)) {
                items.add(item);
            }
        }
        return items;
    }

    private boolean matchesFilter(String reportName, JsonNode item, String filter) {
        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)) {
            return true;
        }
        return switch (reportName) {
            case "change-ledger-preview.json" -> filter.equalsIgnoreCase(textOrEmpty(item, "classification"));
            case "feature-usage-report.json" -> filter.equalsIgnoreCase(textOrEmpty(item, "health"));
            default -> true;
        };
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : "";
    }

    private JsonNode summaryWithoutArray(JsonNode root, String arrayField) {
        if (!(root instanceof ObjectNode objectNode)) {
            return root;
        }
        ObjectNode copy = objectNode.deepCopy();
        copy.remove(arrayField);
        return copy;
    }

    public record PaginatedReportPage(
            String reportName,
            int total,
            int offset,
            int limit,
            JsonNode summary,
            List<JsonNode> items) {
    }
}
