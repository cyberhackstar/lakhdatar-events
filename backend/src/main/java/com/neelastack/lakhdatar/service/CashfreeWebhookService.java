package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.domain.PaymentWebhookEvent;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import com.neelastack.lakhdatar.repository.PaymentWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class CashfreeWebhookService {
    private static final Logger log = LoggerFactory.getLogger(CashfreeWebhookService.class);
    private final PaymentGatewayRouter gateways;
    private final DistributedLockService locks;
    private final PaymentRepository payments;
    private final PaymentWebhookEventRepository events;
    private final OrderService orders;
    private final RefundService refundService;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final AppProperties props;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${app.webhook.max-attempts:12}") private int maxAttempts;
    @Value("${app.webhook.retry-base-delay:2s}") private Duration retryBaseDelay;
    @Value("${app.webhook.retry-max-delay:15m}") private Duration retryMaxDelay;

    private final ExecutorService processingExecutor = new ThreadPoolExecutor(
            4, 12, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
            Thread.ofPlatform().daemon().name("cashfree-webhook-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public CashfreeWebhookService(PaymentGatewayRouter gateways, DistributedLockService locks, PaymentRepository payments, PaymentWebhookEventRepository events,
                                  OrderService orders, RefundService refundService, TransactionTemplate tx, ObjectMapper mapper, AppProperties props) {
        this.gateways = gateways; this.locks = locks; this.payments = payments; this.events = events; this.orders = orders; this.refundService = refundService;
        this.tx = tx; this.mapper = mapper; this.props = props;
    }

    /** Verify + durably persist, then acknowledge. Business fulfillment is asynchronous and recoverable. */
    public void handle(String raw, String signature, String timestamp) {
        if (!validTimestamp(timestamp)) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_TIMESTAMP", "Webhook timestamp is invalid or expired");
        if (!gateways.forProvider(Enums.PaymentProvider.CASHFREE).verifyWebhookSignature(raw, signature, timestamp))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK", "Webhook signature invalid");
        JsonNode n;
        try { n = mapper.readTree(raw); } catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook payload is invalid"); }

        // The signature authenticates the exact raw body. Persist the exact-body hash for audit, but
        // derive the durable event key from Cashfree business identifiers so semantically identical
        // retries are not duplicated merely because JSON formatting/order changed.
        String payloadHash = sha256(raw);
        String eventId = stableEventId(n, payloadHash);
        persistIfAbsent(eventId, n.path("type").asText("unknown"), raw, payloadHash);
        PaymentWebhookEvent stored = events.findByProviderEventId(eventId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_PERSISTENCE_FAILED", "Webhook could not be persisted"));
        if (stored.isProcessed()) return;
        dispatch(eventId);
    }


    private String stableEventId(JsonNode n, String payloadHash) {
        JsonNode data = n.path("data");
        JsonNode order = data.path("order");
        JsonNode payment = data.path("payment");
        JsonNode refund = data.path("refund");
        String type = n.path("type").asText("unknown");
        String orderId = order.path("order_id").asText("");
        if (orderId.isBlank()) orderId = refund.path("order_id").asText("");
        String paymentId = payment.path("cf_payment_id").asText("");
        String refundId = refund.path("refund_id").asText("");
        String eventTime = n.path("event_time").asText("");
        String fingerprint = String.join("|", "CASHFREE", type, orderId, paymentId, refundId, eventTime);
        if (orderId.isBlank() && paymentId.isBlank() && refundId.isBlank() && eventTime.isBlank()) fingerprint = payloadHash;
        return "cashfree:" + sha256(fingerprint);
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
                    e.setProvider("CASHFREE"); e.setProviderEventId(eventId); e.setEventType(type); e.setPayload(raw); e.setPayloadHash(hash); events.saveAndFlush(e);
                }
            });
        } catch (DataIntegrityViolationException ignored) { /* concurrent delivery won the unique key */ }
    }

    private void dispatch(String id) {
        if (claim(id) == 0) return;
        try {
            processingExecutor.submit(() -> processClaimed(id));
        } catch (RejectedExecutionException ex) {
            fail(id, ex);
            EnterpriseLog.warn(log, "cashfree.webhook.processing.queue_full", "event.category", "payment", "webhook.event.id", id);
        }
    }

    private int claim(String id) {
        Instant now = Instant.now();
        Integer result = tx.execute(s -> events.claimForProcessing(id, now, maxAttempts));
        return result == null ? 0 : result;
    }

    private void processClaimed(String id) {
        try {
            PaymentWebhookEvent stored = events.findByProviderEventId(id).orElseThrow(() ->
                    new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_PERSISTENCE_FAILED", "Stored webhook disappeared"));
            JsonNode payload;
            try { payload = mapper.readTree(stored.getPayload()); }
            catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Stored webhook payload is invalid"); }
            process(payload);
            tx.executeWithoutResult(s -> events.markProcessed(id, Instant.now()));
            EnterpriseLog.info(log, "cashfree.webhook.processed", "event.category", "payment", "webhook.event.id", id,
                    "webhook.type", stored.getEventType(), "webhook.attempt", stored.getAttemptCount());
        } catch (RuntimeException ex) {
            fail(id, ex);
        }
    }

    private void fail(String id, RuntimeException ex) {
        PaymentWebhookEvent current = events.findByProviderEventId(id).orElse(null);
        int attempt = current == null ? 1 : Math.max(1, current.getAttemptCount());
        boolean deadLetter = attempt >= maxAttempts;
        Instant next = deadLetter ? null : Instant.now().plus(retryDelay(attempt));
        tx.executeWithoutResult(s -> events.markFailed(id, safe(ex), next, deadLetter));
        EnterpriseLog.error(log, "cashfree.webhook.processing.failed", ex, "event.category", "payment", "webhook.event.id", id,
                "webhook.attempt", attempt, "webhook.dead_letter", deadLetter, "webhook.next_attempt_at", next);
    }

    private Duration retryDelay(int attempt) {
        long base = Math.max(1L, retryBaseDelay.toMillis());
        long max = Math.max(base, retryMaxDelay.toMillis());
        int exponent = Math.min(20, Math.max(0, attempt - 1));
        long raw;
        try { raw = Math.multiplyExact(base, 1L << exponent); } catch (ArithmeticException ex) { raw = max; }
        long bounded = Math.min(raw, max);
        long jitter = Math.max(1L, bounded / 5);
        long offset = java.util.concurrent.ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Duration.ofMillis(Math.max(1000L, Math.min(max, bounded + offset)));
    }

    private void process(JsonNode n) {
        String type = n.path("type").asText(""); JsonNode data = n.path("data"); JsonNode order = data.path("order"); JsonNode pay = data.path("payment"); JsonNode refund = data.path("refund");
        String orderId = order.path("order_id").asText(null); if (orderId == null) orderId = refund.path("order_id").asText(null);
        String paymentId = pay.path("cf_payment_id").asText(null); String status = pay.path("payment_status").asText("");
        if (orderId == null) return;
        Payment p = payments.findByProviderOrderId(orderId).orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_LINKED", "Cashfree webhook arrived before the local payment was linked"));
        if (paymentId != null && !paymentId.isBlank() && pay.hasNonNull("payment_amount")) {
            long amount = toMinorUnits(pay, "payment_amount");
            String currency = pay.path("payment_currency").asText(p.getCurrency());
            Enums.PaymentStatus normalizedStatus = normalizeAttemptStatus(status);
            var providerAttempt = new PaymentGatewayProvider.ProviderPayment(
                    paymentId, orderId, amount, currency, normalizedStatus.name().toLowerCase(Locale.ROOT),
                    pay.path("payment_message").asText(null), 0, null, Enums.PaymentStatus.CAPTURED == normalizedStatus);
            orders.recordProviderAttempt(p, providerAttempt);
        }
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

    private Enums.PaymentStatus normalizeAttemptStatus(String status) {
        if ("SUCCESS".equalsIgnoreCase(status)) return Enums.PaymentStatus.CAPTURED;
        if ("FAILED".equalsIgnoreCase(status)) return Enums.PaymentStatus.FAILED;
        if ("USER_DROPPED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) return Enums.PaymentStatus.CANCELLED;
        return Enums.PaymentStatus.PENDING;
    }

    private long toMinorUnits(JsonNode node, String field) {
        String raw = node.path(field).asText(null);
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook monetary amount is missing");
        try { return new java.math.BigDecimal(raw).movePointRight(2).longValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook monetary amount is invalid"); }
    }

    @Scheduled(fixedDelayString = "${app.webhook.recovery-sweep:15000}")
    void recoverPending() {
        if (!workerEnabled) return;
        locks.withLock("job:webhook-recovery:cashfree", Duration.ofMinutes(2), () -> {
            Instant now = Instant.now();
            int reset = events.resetStaleProcessing("CASHFREE", now.minus(Duration.ofMinutes(10)), now, maxAttempts);
            if (reset > 0) {
                EnterpriseLog.warn(log, "cashfree.webhook.stale_claims_reset", "event.category", "payment", "webhook.reset_count", reset);
            }
            List<PaymentWebhookEvent> due = events.findDueForProcessing("CASHFREE", now);
            for (PaymentWebhookEvent e : due) dispatch(e.getProviderEventId());
        });
    }

    @PreDestroy
    void shutdownExecutor() {
        processingExecutor.shutdown();
        try {
            if (!processingExecutor.awaitTermination(15, TimeUnit.SECONDS)) processingExecutor.shutdownNow();
        } catch (InterruptedException ex) {
            processingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String safe(Throwable e) { String x = e.getMessage(); if (x == null || x.isBlank()) x = e.getClass().getSimpleName(); x = x.replaceAll("[\\r\\n\\t]", " "); return x.length() > 500 ? x.substring(0, 500) : x; }
    private String sha256(String s) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
}
