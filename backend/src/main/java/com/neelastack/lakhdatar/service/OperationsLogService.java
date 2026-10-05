package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Read-only, bounded Loki query facade for the ADMIN Operations Center. */
@Service
@RequiredArgsConstructor
public class OperationsLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationsLogService.class);
    private static final int MAX_LIMIT = 200;
    private static final int MAX_SINCE_MINUTES = 24 * 60;
    private static final List<String> ALLOWED_LEVELS = List.of("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    private final ObjectMapper mapper;
    @Value("${app.observability.logs.enabled:true}") private boolean enabled;
    @Value("${app.observability.loki-url:http://loki:3100}") private String lokiUrl;
    @Value("${app.observability.log-query-timeout-ms:4000}") private long timeoutMs;

    public record LogEntry(Instant timestamp, String service, String level, String action, String message,
                           String correlationId, String container) {}
    public record LogPage(List<LogEntry> items, Instant generatedAt, int limit, int sinceMinutes, boolean available) {}

    public LogPage query(String service, String level, String search, Integer requestedLimit, Integer requestedSinceMinutes) {
        int limit = clamp(requestedLimit == null ? 100 : requestedLimit, 1, MAX_LIMIT);
        int sinceMinutes = clamp(requestedSinceMinutes == null ? 15 : requestedSinceMinutes, 1, MAX_SINCE_MINUTES);
        String normalizedLevel = level == null ? "ALL" : level.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_LEVELS.contains(normalizedLevel)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOG_LEVEL", "Unsupported log level");
        }
        String normalizedService = normalize(service, 64);
        String normalizedSearch = normalize(search, 160);

        if (!enabled) return new LogPage(List.of(), Instant.now(), limit, sinceMinutes, false);

        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(sinceMinutes));
        String query = buildQuery(normalizedService, normalizedSearch);
        try {
            long started = System.nanoTime();
            int fetchLimit = !"ALL".equals(normalizedLevel) ? Math.min(1000, limit * 5) : limit;
            String response = request(query, start, end, fetchLimit);
            List<LogEntry> entries = parse(response);
            if (!"ALL".equals(normalizedLevel)) {
                entries = entries.stream().filter(e -> normalizedLevel.equals(e.level())).limit(limit).toList();
            } else if (entries.size() > limit) {
                entries = entries.subList(0, limit);
            }
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            EnterpriseLog.debug(log, "ops.logs.query.completed",
                    "event.category", "observability", "log.query.service", normalizedService == null ? "ALL" : normalizedService,
                    "log.query.level", normalizedLevel, "log.query.limit", limit, "log.query.since_minutes", sinceMinutes,
                    "log.result_count", entries.size(), "duration.ms", durationMs);
            return new LogPage(entries, Instant.now(), limit, sinceMinutes, true);
        } catch (Exception ex) {
            EnterpriseLog.warn(log, "ops.logs.query.unavailable",
                    "event.category", "observability", "log.store", "loki", "log.query.service", normalizedService == null ? "ALL" : normalizedService,
                    "error.type", ex.getClass().getSimpleName());
            return new LogPage(List.of(), Instant.now(), limit, sinceMinutes, false);
        }
    }

    private String request(String query, Instant start, Instant end, int limit) throws Exception {
        String base = lokiUrl == null ? "http://loki:3100" : lokiUrl.replaceAll("/+$", "");
        String uri = base + "/loki/api/v1/query_range?" +
                "query=" + enc(query) +
                "&start=" + start.toEpochMilli() * 1_000_000L +
                "&end=" + end.toEpochMilli() * 1_000_000L +
                "&limit=" + limit +
                "&direction=backward";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, timeoutMs)))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Loki returned HTTP " + response.statusCode());
        return response.body();
    }

    private List<LogEntry> parse(String body) throws Exception {
        JsonNode result = mapper.readTree(body).path("data").path("result");
        List<LogEntry> entries = new ArrayList<>();
        if (!result.isArray()) return entries;
        for (JsonNode streamResult : result) {
            JsonNode labels = streamResult.path("stream");
            String container = text(labels, "container", "unknown");
            String service = text(labels, "service", container);
            JsonNode values = streamResult.path("values");
            if (!values.isArray()) continue;
            for (JsonNode pair : values) {
                if (!pair.isArray() || pair.size() < 2) continue;
                Instant timestamp;
                try { timestamp = Instant.ofEpochSecond(0, Long.parseLong(pair.get(0).asText())); }
                catch (RuntimeException ex) { continue; }
                Parsed parsed = parseLine(pair.get(1).asText(""));
                entries.add(new LogEntry(timestamp, service, parsed.level(), parsed.action(), parsed.message(), parsed.correlationId(), container));
            }
        }
        return entries.stream().sorted(Comparator.comparing(LogEntry::timestamp).reversed()).toList();
    }

    private Parsed parseLine(String raw) {
        try {
            JsonNode json = mapper.readTree(raw);
            if (json.isObject()) {
                String level = json.path("log").path("level").asText(json.path("level").asText("INFO"));
                String action = json.path("event").path("action").asText(json.path("message").asText("log.entry"));
                String correlation = json.path("correlationId").asText(json.path("correlation_id").asText(null));
                return new Parsed(normalizeLevel(level), action, summarize(json));
            }
        } catch (Exception ignored) { }
        String upper = raw.toUpperCase(Locale.ROOT);
        String level = upper.contains(" ERROR ") || upper.startsWith("ERROR") ? "ERROR"
                : upper.contains(" WARN ") || upper.startsWith("WARN") ? "WARN"
                : upper.contains(" DEBUG ") || upper.startsWith("DEBUG") ? "DEBUG" : "INFO";
        return new Parsed(level, "log.entry", truncate(raw));
    }

    /** Create an operator-friendly summary from a small allow-list of non-sensitive fields. */
    private String summarize(JsonNode json) {
        String message = textAt(json, "message", "log.entry");
        List<String> details = new ArrayList<>();
        add(details, "method", textAt(json, "http.method", null, "http", "method", "method"));
        add(details, "route", textAt(json, "http.route", null, "http", "route", "uri"));
        add(details, "status", textAt(json, "http.status_code", null, "http", "status_code", "status"));
        add(details, "duration", durationValue(json));
        add(details, "outcome", textAt(json, "event.outcome", null, "event", "outcome", "outcome"));
        add(details, "error", textAt(json, "error.code", null, "error", "code"));
        add(details, "errorType", textAt(json, "error.type", null, "error", "type"));
        add(details, "provider", textAt(json, "provider", null));
        add(details, "providerPath", textAt(json, "provider.path", null, "provider", "path"));
        add(details, "order", textAt(json, "order.number", null, "order", "number"));
        add(details, "payment", textAt(json, "payment.status", null, "payment", "status"));
        add(details, "refund", textAt(json, "refund.status", null, "refund", "status"));
        add(details, "result", textAt(json, "checkin.result", null, "checkin", "result"));
        add(details, "gate", textAt(json, "gate", null));
        add(details, "ticketCount", textAt(json, "ticket.count", null, "ticket", "count"));
        add(details, "batch", textAt(json, "batch.size", null, "batch", "size"));
        add(details, "failed", textAt(json, "batch.failed", null, "batch", "failed"));
        return details.isEmpty() ? truncate(message) : truncate(message + " · " + String.join(" · ", details));
    }

    private static String textAt(JsonNode json, String first, String fallback, String... nestedPath) {
        JsonNode direct = json.path(first);
        if (!direct.isMissingNode() && !direct.isNull() && !direct.asText().isBlank()) return direct.asText();
        JsonNode nested = json;
        for (String part : nestedPath) nested = nested.path(part);
        if (!nested.isMissingNode() && !nested.isNull() && !nested.asText().isBlank()) return nested.asText();
        return fallback;
    }

    private static void add(List<String> out, String name, String value) {
        if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) out.add(name + "=" + value);
    }

    private static String durationValue(JsonNode json) {
        String value = textAt(json, "http.response.duration_ms", null, "http", "response", "duration_ms");
        if (value == null) value = textAt(json, "duration.ms", null, "duration", "ms");
        if (value == null) value = textAt(json, "request_time", null);
        return value == null ? null : value + (value.matches("\\d+") ? "ms" : "");
    }

    private String buildQuery(String service, String search) {
        StringBuilder q = new StringBuilder("{environment=\"production\",platform=\"lakhdatar-events\"");
        if (service != null) q.append(",container=\"").append(service.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        q.append("}");
        if (search != null) q.append(" |= \"").append(search.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        return q.toString();
    }

    private static String text(JsonNode node, String field, String fallback) {
        String v = node.path(field).asText();
        return v == null || v.isBlank() ? fallback : v;
    }
    private static String normalize(String value, int max) { if (value == null || value.isBlank()) return null; String v = value.trim(); return v.length() <= max ? v : v.substring(0, max); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String normalizeLevel(String value) { String x = value == null ? "INFO" : value.toUpperCase(Locale.ROOT); return ALLOWED_LEVELS.contains(x) && !"ALL".equals(x) ? x : "INFO"; }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String truncate(String value) { if (value == null) return ""; return value.length() <= 900 ? value : value.substring(0, 900) + "…"; }
    private record Parsed(String level, String action, String message, String correlationId) {
        Parsed(String level, String action, String message) { this(level, action, message, null); }
    }
}
