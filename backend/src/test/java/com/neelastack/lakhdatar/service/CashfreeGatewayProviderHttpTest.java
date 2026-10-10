package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.AppProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the Cashfree HTTP contract without real merchant credentials or network access. */
class CashfreeGatewayProviderHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pg", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/pg";
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void missingReceiptIsEmptyAndCreateAndPaymentLookupUseCashfreeV20250101Contract() throws Exception {
        CashfreeGatewayProvider provider = new CashfreeGatewayProvider(properties(), mapper);

        assertTrue(provider.findOrderByReceipt("LK-TEST12345678").isEmpty(),
                "A missing receipt during recovery should be treated as no existing provider order");

        PaymentGatewayProvider.ProviderOrder created = provider.createOrder(
                10_000, "INR", "LK-TEST12345678", "E2E Customer", "e2e@example.test", "9876543210");
        assertEquals("LK-TEST12345678", created.id());
        assertEquals("session_test_cashfree", created.sessionId());
        assertEquals(10_000, created.amount());
        assertEquals("created", created.status());

        List<PaymentGatewayProvider.ProviderPayment> payments = provider.fetchPaymentsForOrder(created.id());
        assertEquals(1, payments.size());
        assertEquals("cf-payment-123", payments.getFirst().id());
        assertEquals("captured", payments.getFirst().status());
        assertTrue(payments.getFirst().captured());
        assertEquals(10_000, payments.getFirst().amount());
        assertEquals("INR", payments.getFirst().currency());

        assertEquals(3, requests.size());
        CapturedRequest miss = requests.get(0);
        assertEquals("GET", miss.method());
        assertEquals("/pg/orders/LK-TEST12345678", miss.path());

        CapturedRequest create = requests.get(1);
        assertEquals("POST", create.method());
        assertEquals("/pg/orders", create.path());
        assertEquals("test-app-id", create.header("x-client-id"));
        assertEquals("test-secret-key", create.header("x-client-secret"));
        assertEquals("2025-01-01", create.header("x-api-version"));
        assertNotNull(create.header("x-idempotency-key"));
        assertEquals(36, create.header("x-idempotency-key").length(), "Idempotency key must be UUID-shaped");
        JsonNode requestBody = mapper.readTree(create.body());
        assertEquals("LK-TEST12345678", requestBody.path("order_id").asText());
        assertEquals(10_000, requestBody.path("order_amount").decimalValue().movePointRight(2).longValueExact());
        assertEquals("INR", requestBody.path("order_currency").asText());
        assertEquals("9876543210", requestBody.path("customer_details").path("customer_phone").asText());

        CapturedRequest paymentLookup = requests.get(2);
        assertEquals("GET", paymentLookup.method());
        assertEquals("/pg/orders/LK-TEST12345678/payments", paymentLookup.path());
        assertEquals("2025-01-01", paymentLookup.header("x-api-version"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("x-client-id"),
                exchange.getRequestHeaders().getFirst("x-client-secret"),
                exchange.getRequestHeaders().getFirst("x-api-version"),
                exchange.getRequestHeaders().getFirst("x-idempotency-key"), body));

        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && "/pg/orders/LK-TEST12345678".equals(path)) {
            respond(exchange, 404, "{\"code\":\"order_not_found\"}");
        } else if ("POST".equals(exchange.getRequestMethod()) && "/pg/orders".equals(path)) {
            respond(exchange, 200, "{\"order_id\":\"LK-TEST12345678\",\"order_amount\":100.00,\"order_currency\":\"INR\",\"order_status\":\"ACTIVE\",\"payment_session_id\":\"session_test_cashfree\"}");
        } else if ("GET".equals(exchange.getRequestMethod()) && "/pg/orders/LK-TEST12345678/payments".equals(path)) {
            respond(exchange, 200, "[{\"cf_payment_id\":\"cf-payment-123\",\"payment_amount\":100.00,\"payment_currency\":\"INR\",\"payment_status\":\"SUCCESS\",\"payment_message\":\"Payment successful\"}]");
        } else {
            respond(exchange, 404, "{\"code\":\"not_found\"}");
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Jwt("unit-test-jwt-secret-012345678901234567890", Duration.ofMinutes(30), Duration.ofDays(14)),
                new AppProperties.Security("unit-test-ticket-view-secret-01234567890", false, Duration.ofHours(24)),
                new AppProperties.Qr("unit-test-qr-secret-012345678901234", 512),
                new AppProperties.Reservation(Duration.ofMinutes(12), Duration.ofSeconds(30)),
                new AppProperties.Branding("Neelastack", "https://neelastack.com", "/assets/neelastack-logo.png", "", true, "Powered by Neelastack", "Build modern digital experiences.", "Explore Neelastack"),
                new AppProperties.Checkout(20),
                new AppProperties.Bootstrap(false, "", "", "", "Lakhdatar Events", "lakhdatar-events", "dandiya-night-2026"),
                new AppProperties.Cors("http://localhost:4200"),
                new AppProperties.RateLimit(10, 20, 240, 60),
                new AppProperties.Razorpay("", "", "", "https://example.invalid/v1", 120000, 30000, 3000, 8000),
                new AppProperties.Cashfree("test-app-id", "test-secret-key", baseUrl, "2025-01-01", 120000, 30000, 3000, 8000, 300000),
                new AppProperties.Payment(120000, 30000),
                new AppProperties.InitialAdmin(false, ""),
                new AppProperties.Cloudinary("", "", "", "neelastack-events", 5242880),
                "https://events.example.test"
        );
    }

    private record CapturedRequest(String method, String path, String clientId, String clientSecret,
                                   String apiVersion, String idempotencyKey, String body) {
        String header(String name) {
            return switch (name.toLowerCase()) {
                case "x-client-id" -> clientId;
                case "x-client-secret" -> clientSecret;
                case "x-api-version" -> apiVersion;
                case "x-idempotency-key" -> idempotencyKey;
                default -> null;
            };
        }
    }
}
