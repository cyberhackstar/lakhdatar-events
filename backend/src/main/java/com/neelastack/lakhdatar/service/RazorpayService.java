package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Service
public class RazorpayService {
    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);
    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final AppProperties.Razorpay props;

    public RazorpayService(AppProperties properties) {
        this.props = properties.razorpay();
        if (props.baseUrl() == null || props.baseUrl().isBlank()) throw new IllegalStateException("Razorpay base URL is missing");
        this.objectMapper = new ObjectMapper();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.max(1000, props.httpConnectTimeoutMs()))).build();
    }

    public record RazorpayOrderRef(String razorpayOrderId, String keyId) {}
    public record ProviderOrder(String id, long amount, String currency, String receipt, String status) {}
    public record ProviderPayment(String id, String orderId, long amount, String currency, String status, String errorDescription, long amountRefunded, String refundStatus, boolean captured) {}
    public record RefundRef(String refundId, String status, long amount) {}
    public record ProviderRefund(String id, String status, long amount, String receipt) {}

    public RazorpayOrderRef createOrder(long amount, String currency, String receipt) {
        if (props.keyId() == null || props.keyId().isBlank() || props.keySecret() == null || props.keySecret().isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", "Payment gateway is not configured");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", amount);
        request.put("currency", currency);
        request.put("receipt", receipt);
        request.put("partial_payment", false);
        JsonNode n = post("/orders", request);
        String id = n.path("id").asText(null);
        if (id == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Payment provider did not return an order ID");
        return new RazorpayOrderRef(id, props.keyId());
    }

    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return signature != null && constantTime(hmac(orderId + "|" + paymentId, props.keySecret()), signature);
    }

    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return signature != null && props.webhookSecret() != null && !props.webhookSecret().isBlank()
                && constantTime(hmac(rawBody, props.webhookSecret()), signature);
    }

    public ProviderPayment fetchPayment(String paymentId) { return providerPayment(get("/payments/" + encode(paymentId))); }
    public ProviderOrder fetchOrder(String orderId) {
        JsonNode n = get("/orders/" + encode(orderId));
        return new ProviderOrder(n.path("id").asText(null), n.path("amount").asLong(-1), n.path("currency").asText(null), n.path("receipt").asText(null), n.path("status").asText(null));
    }

    public List<ProviderPayment> fetchPaymentsForOrder(String orderId) {
        JsonNode n = get("/orders/" + encode(orderId) + "/payments");
        List<ProviderPayment> out = new ArrayList<>(); for (JsonNode x : n.path("items")) out.add(providerPayment(x)); return out;
    }

    public Optional<ProviderOrder> findOrderByReceipt(String receipt) {
        JsonNode n = get("/orders?receipt=" + encode(receipt) + "&count=100");
        for (JsonNode x : n.path("items")) {
            if (receipt.equals(x.path("receipt").asText(null)))
                return Optional.of(new ProviderOrder(x.path("id").asText(null), x.path("amount").asLong(-1), x.path("currency").asText(null), x.path("receipt").asText(null), x.path("status").asText(null)));
        }
        return Optional.empty();
    }

    public RefundRef refund(String paymentId, long amount, String reason, String receipt, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("amount", amount); body.put("speed", "normal");
        if (reason != null && !reason.isBlank()) body.put("notes", Map.of("reason", reason));
        body.put("receipt", receipt);
        JsonNode n = post("/payments/" + encode(paymentId) + "/refund", body, Map.of("X-Refund-Idempotency", idempotencyKey));
        String id = n.path("id").asText(null);
        if (id == null) throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Payment provider did not return a refund ID");
        return new RefundRef(id, n.path("status").asText(null), n.path("amount").asLong(-1));
    }

    public Optional<ProviderRefund> fetchRefund(String paymentId, String refundId) {
        JsonNode n = get("/payments/" + encode(paymentId) + "/refunds/" + encode(refundId));
        return Optional.of(new ProviderRefund(n.path("id").asText(null), n.path("status").asText(null), n.path("amount").asLong(-1), n.path("receipt").asText(null)));
    }

    public List<ProviderRefund> fetchRefundsForPayment(String paymentId) {
        JsonNode n = get("/payments/" + encode(paymentId) + "/refunds");
        List<ProviderRefund> out = new ArrayList<>();
        for (JsonNode x : n.path("items")) out.add(new ProviderRefund(x.path("id").asText(null), x.path("status").asText(null), x.path("amount").asLong(-1), x.path("receipt").asText(null)));
        return out;
    }

    private ProviderPayment providerPayment(JsonNode n) {
        return new ProviderPayment(n.path("id").asText(null), n.path("order_id").asText(null), n.path("amount").asLong(-1),
                n.path("currency").asText(null), n.path("status").asText(null), n.path("error_description").asText(null),
                n.path("amount_refunded").asLong(0), n.path("refund_status").asText(null), n.path("captured").asBoolean("captured".equalsIgnoreCase(n.path("status").asText())));
    }

    private JsonNode get(String path) {
        try { return send("GET", path, null, Map.of()); }
        catch (ApiException e) { throw e; }
        catch (Exception e) {
            EnterpriseLog.warn(log, "payment.provider.http.unavailable", "event.category", "payment", "provider", "RAZORPAY",
                    "http.method", "GET", "provider.path", path, "error.type", e.getClass().getSimpleName());
            throw providerException(e);
        }
    }

    private JsonNode post(String path, Object body) { return post(path, body, Map.of()); }

    private JsonNode post(String path, Object body, Map<String,String> headers) {
        try { return send("POST", path, objectMapper.writeValueAsString(body), headers); }
        catch (Exception e) {
            EnterpriseLog.warn(log, "payment.provider.http.unavailable", "event.category", "payment", "provider", "RAZORPAY",
                    "http.method", "POST", "provider.path", path, "error.type", e.getClass().getSimpleName());
            throw providerException(e);
        }
    }

    private JsonNode send(String method, String path, String body, Map<String,String> headers) throws Exception {
        long started = System.nanoTime();
        EnterpriseLog.debug(log, "payment.provider.http.started", "event.category", "payment", "provider", "RAZORPAY", "http.method", method, "provider.path", path);
        String auth = Base64.getEncoder().encodeToString((props.keyId() + ":" + props.keySecret()).getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(props.baseUrl() + path))
                .timeout(Duration.ofMillis(Math.max(2000, props.httpReadTimeoutMs())))
                .header("Authorization", "Basic " + auth).header("Content-Type", "application/json");
        headers.forEach(builder::header);
        HttpRequest request = "GET".equals(method) ? builder.GET().build() : builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        if (response.statusCode() == 404) {
            EnterpriseLog.warn(log, "payment.provider.http.not_found", "event.category", "payment", "provider", "RAZORPAY", "http.method", method, "provider.path", path, "http.status_code", 404, "duration.ms", durationMs);
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_PROVIDER_NOT_FOUND", "Razorpay resource was not found");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            EnterpriseLog.warn(log, "payment.provider.http.failed", "event.category", "payment", "provider", "RAZORPAY", "http.method", method, "provider.path", path, "http.status_code", response.statusCode(), "duration.ms", durationMs);
            throw new IllegalStateException("Razorpay HTTP " + response.statusCode());
        }
        EnterpriseLog.debug(log, "payment.provider.http.succeeded", "event.category", "payment", "provider", "RAZORPAY", "http.method", method, "provider.path", path, "http.status_code", response.statusCode(), "duration.ms", durationMs);
        return objectMapper.readTree(response.body());
    }

    private ApiException providerException(Exception ignored) { return new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_UNAVAILABLE", "Payment provider is temporarily unavailable"); }
    private String encode(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private String hmac(String value, String secret) { try { Mac m=Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(m.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }
    private boolean constantTime(String a,String b){ return a!=null && b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII)); }
}
