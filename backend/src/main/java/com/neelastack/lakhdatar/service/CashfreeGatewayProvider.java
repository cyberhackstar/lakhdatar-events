package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Component
public class CashfreeGatewayProvider implements PaymentGatewayProvider {
    private static final Logger log = LoggerFactory.getLogger(CashfreeGatewayProvider.class);
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public CashfreeGatewayProvider(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, props.cashfree().httpConnectTimeoutMs())))
                .build();
    }

    @Override public Enums.PaymentProvider provider() { return Enums.PaymentProvider.CASHFREE; }

    @Override public boolean isConfigured() {
        return present(props.cashfree().appId()) && present(props.cashfree().secretKey());
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder(URI.create(props.cashfree().baseUrl().replaceAll("/$", "") + path))
                .timeout(Duration.ofMillis(Math.max(2000, props.cashfree().httpReadTimeoutMs())))
                .header("x-request-id", UUID.randomUUID().toString())
                .header("x-client-id", props.cashfree().appId())
                .header("x-client-secret", props.cashfree().secretKey())
                .header("x-api-version", props.cashfree().apiVersion())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    private JsonNode call(HttpRequest request) {
        long started = System.nanoTime();
        String requestPath = request.uri().getPath();
        EnterpriseLog.debug(log, "payment.provider.http.started", "event.category", "payment", "provider", "CASHFREE", "http.method", request.method(), "provider.path", requestPath);
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = (System.nanoTime() - started) / 1_000_000L;
            if (response.statusCode() == 404) {
                EnterpriseLog.warn(log, "payment.provider.http.not_found", "event.category", "payment", "provider", "CASHFREE", "http.method", request.method(), "provider.path", requestPath, "http.status_code", 404, "duration.ms", durationMs);
                throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_PROVIDER_NOT_FOUND", "Cashfree resource was not found");
            }
            if (response.statusCode() / 100 != 2) {
                EnterpriseLog.warn(log, "payment.provider.http.failed", "event.category", "payment", "provider", "CASHFREE", "http.method", request.method(), "provider.path", requestPath, "http.status_code", response.statusCode(), "duration.ms", durationMs);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Cashfree payment service returned an error");
            }
            EnterpriseLog.debug(log, "payment.provider.http.succeeded", "event.category", "payment", "provider", "CASHFREE", "http.method", request.method(), "provider.path", requestPath, "http.status_code", response.statusCode(), "duration.ms", durationMs);
            return mapper.readTree(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            EnterpriseLog.warn(log, "payment.provider.http.failed", "event.category", "payment", "provider", "CASHFREE", "http.method", request.method(), "provider.path", requestPath, "error.type", e.getClass().getSimpleName(), "duration.ms", (System.nanoTime()-started)/1_000_000L);
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_PROVIDER_UNAVAILABLE", "Cashfree payment service is temporarily unavailable");
        }
    }

    @Override
    public ProviderOrder createOrder(long amount, String currency, String receipt, String customerName, String customerEmail, String customerPhone) {
        try {
            var body = mapper.createObjectNode();
            body.put("order_id", receipt);
            body.put("order_amount", BigDecimal.valueOf(amount, 2));
            body.put("order_currency", currency);
            var customer = body.putObject("customer_details");
            customer.put("customer_id", receipt);
            customer.put("customer_name", customerName == null || customerName.isBlank() ? "Customer" : customerName);
            customer.put("customer_email", customerEmail == null ? "" : customerEmail);
            String normalizedPhone = customerPhone == null ? "" : customerPhone.replaceAll("\\D", "");
            if (!normalizedPhone.isBlank()) customer.put("customer_phone", normalizedPhone);
            var meta = body.putObject("order_meta");
            meta.put("return_url", props.publicBaseUrl() + "/payment/success?order_id={order_id}");
            meta.put("notify_url", props.publicBaseUrl() + "/api/v1/webhooks/cashfree");
            HttpRequest request = req("/orders")
                    .header("x-idempotency-key", deterministicIdempotencyKey("order", receipt))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            JsonNode n = call(request);
            return new ProviderOrder(n.path("order_id").asText(receipt), null, n.path("payment_session_id").asText(null), amount, currency, n.path("order_id").asText(receipt), normalizeOrderStatus(n.path("order_status").asText("UNKNOWN")));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Cashfree order could not be created");
        }
    }

    @Override public ProviderOrder fetchOrder(String id) {
        JsonNode n = call(req("/orders/" + enc(id)).GET().build());
        long amount = toMinorUnits(n, "order_amount");
        String orderId = n.path("order_id").asText(id);
        return new ProviderOrder(orderId, null, n.path("payment_session_id").asText(null), amount, n.path("order_currency").asText("INR"), orderId, normalizeOrderStatus(n.path("order_status").asText("UNKNOWN")));
    }

    @Override public Optional<ProviderOrder> findOrderByReceipt(String receipt) {
        try { return Optional.of(fetchOrder(receipt)); }
        catch (ApiException e) {
            if (e.status() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw e;
        }
    }

    @Override public List<ProviderPayment> fetchPaymentsForOrder(String id) {
        JsonNode n = call(req("/orders/" + enc(id) + "/payments").GET().build());
        List<ProviderPayment> out = new ArrayList<>();
        if (n.isArray()) for (JsonNode x : n) {
            long amount = toMinorUnits(x, "payment_amount");
            String rawStatus = x.path("payment_status").asText("UNKNOWN");
            String currency = x.path("payment_currency").asText("INR");
            out.add(new ProviderPayment(x.path("cf_payment_id").asText(null), id, amount, currency, normalizePaymentStatus(rawStatus), x.path("payment_message").asText(null), 0, null, "SUCCESS".equalsIgnoreCase(rawStatus)));
        }
        return out;
    }

    @Override public ProviderPayment fetchPayment(String id) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "PROVIDER_OPERATION_UNSUPPORTED", "Direct Cashfree payment lookup is not required; order payment status is fetched server-side");
    }

    /** Cashfree checkout confirmation is server-to-server; there is no client-signature verification path here. Fail closed. */
    @Override public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) { return false; }

    @Override public boolean verifyWebhookSignature(String raw, String signature, String timestamp) {
        if (!present(signature) || !present(timestamp) || !present(props.cashfree().secretKey()) || raw == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // Cashfree signs webhook payloads with the merchant Secret Key; there is no separate webhook secret in this integration.
            mac.init(new SecretKeySpec(props.cashfree().secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal((timestamp + raw).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    @Override public ProviderRefund refund(String paymentId, String orderId, long amount, String reason, String receipt, String key) {
        String idempotencyKey = deterministicIdempotencyKey("refund", key == null || key.isBlank() ? receipt : key);
        try {
            var body = mapper.createObjectNode();
            body.put("refund_id", receipt);
            body.put("refund_amount", BigDecimal.valueOf(amount, 2));
            body.put("refund_note", reason == null ? "Event refund" : reason);
            HttpRequest request = req("/orders/" + enc(orderId) + "/refunds")
                    .header("x-idempotency-key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            JsonNode n = call(request);
            long refundAmount = n.hasNonNull("refund_amount") ? toMinorUnits(n, "refund_amount") : amount;
            return new ProviderRefund(n.path("refund_id").asText(receipt), n.path("refund_status").asText("PENDING"), refundAmount, receipt);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Cashfree refund could not be created");
        }
    }

    @Override public List<ProviderRefund> fetchRefundsForOrder(String orderId) {
        JsonNode n = call(req("/orders/" + enc(orderId) + "/refunds").GET().build());
        List<ProviderRefund> out = new ArrayList<>();
        JsonNode items = n.isArray() ? n : n.path("refunds");
        if (items.isArray()) for (JsonNode x : items) out.add(new ProviderRefund(x.path("refund_id").asText(null), x.path("refund_status").asText("PENDING"), toMinorUnits(x, "refund_amount"), x.path("refund_id").asText(null)));
        return out;
    }


    @Override public List<ProviderRefund> fetchRefundsForPayment(String orderId) { return fetchRefundsForOrder(orderId); }
    @Override public Optional<ProviderRefund> fetchRefund(String orderId, String refundId) {
        try {
            JsonNode x = call(req("/orders/" + enc(orderId) + "/refunds/" + enc(refundId)).GET().build());
            return Optional.of(new ProviderRefund(x.path("refund_id").asText(refundId), x.path("refund_status").asText("PENDING"), toMinorUnits(x, "refund_amount"), x.path("refund_id").asText(refundId)));
        } catch (ApiException e) {
            if (e.status() == HttpStatus.NOT_FOUND) return Optional.empty();
            throw e;
        }
    }


    /** Cashfree requires a UUID-shaped idempotency key for safe retries. Derive it deterministically
     * from our stable local request identity so a lost provider response can be replayed safely. */
    private String deterministicIdempotencyKey(String operation, String stableKey) {
        return UUID.nameUUIDFromBytes(("neelastack:cashfree:" + operation + ":" + stableKey)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private long toMinorUnits(JsonNode node, String field) {
        String raw = node.path(field).asText(null);
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Cashfree response did not contain a valid amount");
        try {
            return new BigDecimal(raw).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Cashfree returned an invalid monetary amount");
        }
    }

    private String normalizeOrderStatus(String s) {
        if ("PAID".equalsIgnoreCase(s)) return "paid";
        if ("ACTIVE".equalsIgnoreCase(s) || "PENDING".equalsIgnoreCase(s)) return "created";
        return s == null ? "unknown" : s.toLowerCase(Locale.ROOT);
    }

    private String normalizePaymentStatus(String s) {
        if ("SUCCESS".equalsIgnoreCase(s)) return "captured";
        if ("FAILED".equalsIgnoreCase(s) || "USER_DROPPED".equalsIgnoreCase(s)) return "failed";
        return s == null ? "unknown" : s.toLowerCase(Locale.ROOT);
    }

    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
