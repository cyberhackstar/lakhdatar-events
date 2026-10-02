package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.domain.PaymentWebhookEvent;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import com.neelastack.lakhdatar.repository.PaymentWebhookEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class CashfreeWebhookService {
    private final PaymentGatewayRouter gateways;
    private final PaymentRepository payments;
    private final PaymentWebhookEventRepository events;
    private final OrderService orders;
    private final RefundService refundService;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public CashfreeWebhookService(PaymentGatewayRouter gateways, PaymentRepository payments, PaymentWebhookEventRepository events,
                                  OrderService orders, RefundService refundService, TransactionTemplate tx, ObjectMapper mapper, AppProperties props) {
        this.gateways = gateways; this.payments = payments; this.events = events; this.orders = orders; this.refundService = refundService;
        this.tx = tx; this.mapper = mapper; this.props = props;
    }

    public void handle(String raw, String signature, String timestamp) {
        if (!validTimestamp(timestamp)) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_TIMESTAMP", "Webhook timestamp is invalid or expired");
        if (!gateways.forProvider(Enums.PaymentProvider.CASHFREE).verifyWebhookSignature(raw, signature, timestamp))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK", "Webhook signature invalid");
        JsonNode n;
        try { n = mapper.readTree(raw); } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook payload is invalid"); }

        // The signature authenticates the exact raw body. Use its hash as the primary idempotency identity so
        // two distinct provider events cannot collapse onto the same composite field tuple.
        String eventId = "cashfree:" + sha256(raw);
        persistIfAbsent(eventId, n.path("type").asText("unknown"), raw, sha256(raw));
        PaymentWebhookEvent stored = events.findByProviderEventId(eventId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_PERSISTENCE_FAILED", "Webhook could not be persisted"));
        if (stored.isProcessed()) return;
        if (claim(eventId) == 0) return;
        try {
            process(n);
            tx.executeWithoutResult(s -> events.markProcessed(eventId, Instant.now()));
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(s -> events.markFailed(eventId, safe(ex)));
            throw ex;
        }
    }

    private boolean validTimestamp(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            long raw = Long.parseLong(value.trim());
            long millis = Math.abs(raw) < 1_000_000_000_000L ? Math.multiplyExact(raw, 1000L) : raw;
            long skew = Math.abs(Instant.now().toEpochMilli() - millis);
            return skew <= props.cashfree().webhookToleranceMs();
        } catch (Exception e) { return false; }
    }

    private void persistIfAbsent(String eventId, String type, String raw, String hash) {
        try {
            tx.executeWithoutResult(s -> {
                if (events.findByProviderEventId(eventId).isEmpty()) {
                    PaymentWebhookEvent e = new PaymentWebhookEvent();
                    e.setProviderEventId(eventId); e.setEventType(type); e.setPayload(raw); e.setPayloadHash(hash); events.saveAndFlush(e);
                }
            });
        } catch (DataIntegrityViolationException ignored) { /* concurrent delivery won the unique key */ }
    }

    private int claim(String id) { Integer result = tx.execute(s -> events.claimForProcessing(id, Instant.now())); return result == null ? 0 : result; }

    private void process(JsonNode n) {
        String type = n.path("type").asText(""); JsonNode data = n.path("data"); JsonNode order = data.path("order"); JsonNode pay = data.path("payment"); JsonNode refund = data.path("refund");
        String orderId = order.path("order_id").asText(null); if (orderId == null) orderId = refund.path("order_id").asText(null);
        String paymentId = pay.path("cf_payment_id").asText(null); String status = pay.path("payment_status").asText("");
        if (orderId == null) return;
        Payment p = payments.findByProviderOrderId(orderId).orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_LINKED", "Cashfree webhook arrived before the local payment was linked"));
        if (refund.hasNonNull("refund_id")) {
            String refundId = refund.path("refund_id").asText(null); long refundAmount = toMinorUnits(refund, "refund_amount"); String refundStatus = refund.path("refund_status").asText("");
            if (refundId != null && refundAmount >= 0) refundService.reconcileProviderRefund(p.getId(), refundId, refundStatus, refundAmount);
            return;
        }
        if ("SUCCESS".equalsIgnoreCase(status) || type.toUpperCase().contains("SUCCESS")) {
            long amount = toMinorUnits(pay, "payment_amount"); String currency = pay.path("payment_currency").asText(null);
            if (amount != p.getAmountMinor() || currency == null || !currency.equalsIgnoreCase(p.getCurrency()))
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EVENT_MISMATCH", "Cashfree payment does not match the local order");
            var normalized = new PaymentGatewayProvider.ProviderPayment(paymentId, orderId, amount, currency, "captured", pay.path("payment_message").asText(null), 0, null, true);
            var result = orders.reconcileCapturedPayment(p.getId(), normalized);
            if ("REFUND_PENDING".equals(result.status())) orders.completeQueuedRefundIfNeeded(p.getId(), "Cashfree payment captured after checkout recovery");
        }
    }

    private long toMinorUnits(JsonNode node, String field) {
        String raw = node.path(field).asText(null);
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook monetary amount is missing");
        try { return new java.math.BigDecimal(raw).movePointRight(2).longValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook monetary amount is invalid"); }
    }

    private String safe(Throwable e) { String x = e.getMessage(); if (x == null || x.isBlank()) x = e.getClass().getSimpleName(); x = x.replaceAll("[\r\n\t]", " "); return x.length() > 500 ? x.substring(0, 500) : x; }
    private String sha256(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
