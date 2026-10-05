package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.domain.PaymentWebhookEvent;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import com.neelastack.lakhdatar.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private final RazorpayService razorpay;
    private final DistributedLockService locks;
    private final PaymentGatewayRouter gateways;
    private final PaymentWebhookEventRepository events;
    private final PaymentRepository payments;
    private final OrderService orders;
    private final RefundService refunds;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    @Value("${app.worker.enabled:true}")
    private boolean workerEnabled;
    @Value("${app.webhook.max-attempts:12}")
    private int maxAttempts;
    @Value("${app.webhook.retry-base-delay:2s}")
    private Duration retryBaseDelay;
    @Value("${app.webhook.retry-max-delay:15m}")
    private Duration retryMaxDelay;

    /**
     * Webhook HTTP handlers only authenticate + durably persist. Business processing is handed to a
     * bounded executor so a slow payment provider/database never consumes the provider's retry window
     * or unbounded application memory. Worker nodes recover anything left pending after a restart.
     */
    private final ExecutorService processingExecutor = new ThreadPoolExecutor(
            4, 12, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
            Thread.ofPlatform().daemon().name("payment-webhook-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public void handle(String raw, String signature, String headerEventId) {
        long started = System.nanoTime();
        if (signature == null || !razorpay.verifyWebhookSignature(raw, signature)) {
            EnterpriseLog.warn(log, "webhook.signature.rejected", "event.category", "payment", "error.code", "INVALID_WEBHOOK");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK", "Webhook signature invalid");
        }
        JsonNode n;
        try {
            n = mapper.readTree(raw);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Webhook payload is invalid");
        }
        String headerId = normalizeEventId(headerEventId);
        String payloadId = normalizeEventId(n.path("id").asText(null));
        if (headerId != null && payloadId != null && !headerId.equals(payloadId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_ID", "Webhook event identifiers do not match");
        }
        final String providerEventId = headerId != null ? headerId : (payloadId != null ? payloadId : sha256(raw));
        String eventType = n.path("event").asText("unknown");
        persistIfAbsent(providerEventId, eventType, raw, sha256(raw));
        PaymentWebhookEvent stored = events.findByProviderEventId(providerEventId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_PERSISTENCE_FAILED", "Webhook could not be persisted"));
        if (stored.isProcessed()) {
            EnterpriseLog.debug(log, "webhook.duplicate_ignored", "event.category", "payment", "webhook.event.id", providerEventId, "webhook.type", eventType);
            return;
        }
        dispatch(providerEventId);
        EnterpriseLog.info(log, "webhook.acknowledged", "event.category", "payment", "webhook.event.id", providerEventId,
                "webhook.type", eventType, "duration.ms", (System.nanoTime()-started)/1_000_000L);
    }

    private void persistIfAbsent(String eventId, String type, String raw, String hash) {
        try {
            tx.executeWithoutResult(s -> {
                if (events.findByProviderEventId(eventId).isEmpty()) {
                    PaymentWebhookEvent e = new PaymentWebhookEvent();
                    e.setProvider("RAZORPAY"); e.setProviderEventId(eventId);
                    e.setEventType(type);
                    e.setPayload(raw);
                    e.setPayloadHash(hash);
                    events.saveAndFlush(e);
                }
            });
        } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
            // Concurrent delivery won the provider-event unique key; the canonical row can be read below.
        }
    }

    private void dispatch(String providerEventId) {
        if (claim(providerEventId) == 0) return;
        try {
            processingExecutor.submit(() -> processClaimed(providerEventId));
        } catch (RejectedExecutionException ex) {
            fail(providerEventId, ex);
            EnterpriseLog.warn(log, "webhook.processing.queue_full", "event.category", "payment", "webhook.event.id", providerEventId);
        }
    }

    private int claim(String id) {
        Instant now = Instant.now();
        Integer result = tx.execute(s -> events.claimForProcessing(id, now, maxAttempts));
        return result == null ? 0 : result;
    }

    private void processClaimed(String providerEventId) {
        try {
            PaymentWebhookEvent stored = events.findByProviderEventId(providerEventId).orElseThrow(() ->
                    new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_PERSISTENCE_FAILED", "Stored webhook disappeared"));
            process(stored.getPayload());
            tx.executeWithoutResult(s -> events.markProcessed(providerEventId, Instant.now()));
            EnterpriseLog.info(log, "webhook.processed", "event.category", "payment", "webhook.event.id", providerEventId,
                    "webhook.type", stored.getEventType(), "attempt", stored.getAttemptCount());
        } catch (RuntimeException ex) {
            fail(providerEventId, ex);
        }
    }

    private void fail(String providerEventId, RuntimeException ex) {
        PaymentWebhookEvent current = events.findByProviderEventId(providerEventId).orElse(null);
        int attempt = current == null ? 1 : Math.max(1, current.getAttemptCount());
        boolean deadLetter = attempt >= maxAttempts;
        Instant next = deadLetter ? null : Instant.now().plus(retryDelay(attempt));
        tx.executeWithoutResult(s -> events.markFailed(providerEventId, safeError(ex), next, deadLetter));
        EnterpriseLog.error(log, "webhook.processing.failed", ex, "event.category", "payment", "webhook.event.id", providerEventId,
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

    private void process(String payload) {
        JsonNode event;
        try {
            event = mapper.readTree(payload);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEBHOOK_BODY", "Stored webhook payload is invalid");
        }

        String type = event.path("event").asText("");
        if (type.startsWith("refund.")) {
            processRazorpayRefund(event);
            return;
        }

        JsonNode pe = event.path("payload").path("payment").path("entity");
        String orderId = pe.path("order_id").asText(null);
        String paymentId = pe.path("id").asText(null);
        if (("payment.captured".equals(type) || "payment.authorized".equals(type)) && orderId != null && paymentId != null) {
            Payment p = payments.findByRazorpayOrderId(orderId).orElseThrow(() ->
                    new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_LINKED", "Payment event arrived before the local payment was linked"));
            long amount = pe.path("amount").asLong(-1);
            String currency = pe.path("currency").asText(null);
            if (p.getAmountMinor() != amount || p.getCurrency() == null || !p.getCurrency().equalsIgnoreCase(currency)) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_EVENT_MISMATCH", "Payment webhook does not match the local order");
            }
            var provider = gateways.forProvider(Enums.PaymentProvider.RAZORPAY).fetchPayment(paymentId);
            if (orders.reconcileProviderRefundsIfPresent(p.getId(), provider)) {
                return;
            } else if ("captured".equalsIgnoreCase(provider.status())) {
                var result = orders.reconcileCapturedPayment(p.getId(), provider);
                if ("REFUND_PENDING".equals(result.status())) {
                    orders.completeQueuedRefundIfNeeded(p.getId(), "Reservation expired, event closed, or order no longer payable before payment capture");
                }
            } else if ("authorized".equalsIgnoreCase(provider.status())) {
                final Long localPaymentId = p.getId();
                tx.executeWithoutResult(s -> payments.findByIdForUpdate(localPaymentId).ifPresent(locked -> {
                    var st = locked.getStatus();
                    if (st == Enums.PaymentStatus.CREATED || st == Enums.PaymentStatus.PENDING || st == Enums.PaymentStatus.PAYMENT_INITIATED) {
                        orders.transitionPaymentForWebhook(locked, Enums.PaymentStatus.AUTHORIZED);
                        locked.setProviderPaymentId(paymentId);
                        locked.setRazorpayPaymentId(paymentId);
                        locked.setProviderLastError(null);
                        payments.save(locked);
                    }
                }));
            } else {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_STATE_PENDING", "Provider has not reached a terminal captured/refunded state");
            }
        } else if ("payment.failed".equals(type) && orderId != null) {
            if (payments.findByRazorpayOrderId(orderId).isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_LINKED", "Payment failed event arrived before the local payment was linked");
            }
            orders.failPayment(orderId, pe.path("error_description").asText("Payment failed"));
        }
    }

    private void processRazorpayRefund(JsonNode event) {
        JsonNode refundEntity = event.path("payload").path("refund").path("entity");
        String providerRefundId = refundEntity.path("id").asText(null);
        String providerPaymentId = refundEntity.path("payment_id").asText(null);
        long amountMinor = refundEntity.path("amount").asLong(-1);
        String status = refundEntity.path("status").asText("");
        String receipt = refundEntity.path("receipt").asText(null);

        if (providerRefundId == null || providerRefundId.isBlank() || providerPaymentId == null || providerPaymentId.isBlank() || amountMinor <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_WEBHOOK", "Razorpay refund webhook is missing required fields");
        }
        Payment payment = payments.findByProviderPaymentId(providerPaymentId).orElse(null);
        if (payment == null) {
            // A refund can arrive before the successful payment webhook is persisted locally.
            // Recover the trusted provider order from the provider payment itself instead of
            // rejecting a valid refund until another webhook happens to arrive.
            var providerPayment = razorpay.fetchPayment(providerPaymentId);
            payment = payments.findByRazorpayOrderId(providerPayment.orderId()).orElseGet(() ->
                    payments.findByProviderOrderId(providerPayment.orderId()).orElseThrow(() ->
                            new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_LINKED", "Razorpay refund arrived before the local payment was linked")));
        }
        if (payment.getAmountMinor() < amountMinor) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_EVENT_MISMATCH", "Razorpay refund exceeds the local payment amount");
        }
        refunds.reconcileProviderRefund(payment.getId(), providerRefundId, status, amountMinor, receipt);
    }

    @Scheduled(fixedDelayString = "${app.webhook.recovery-sweep:15000}")
    void recoverPending() {
        if (!workerEnabled) return;
        locks.withLock("job:webhook-recovery:razorpay", Duration.ofMinutes(2), () -> {
            Instant now = Instant.now();
            int reset = events.resetStaleProcessing("RAZORPAY", now.minus(Duration.ofMinutes(10)), now, maxAttempts);
            if (reset > 0) {
                EnterpriseLog.warn(log, "webhook.stale_claims_reset", "event.category", "payment", "webhook.reset_count", reset);
            }
            List<PaymentWebhookEvent> due = events.findDueForProcessing("RAZORPAY", now);
            for (PaymentWebhookEvent e : due) dispatch(e.getProviderEventId());
        });
    }

    @jakarta.annotation.PreDestroy
    public void shutdownProcessingExecutor() {
        processingExecutor.shutdown();
        try {
            if (!processingExecutor.awaitTermination(15, TimeUnit.SECONDS)) processingExecutor.shutdownNow();
        } catch (InterruptedException ex) {
            processingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private String normalizeEventId(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.matches("[A-Za-z0-9._:-]{8,150}") ? s : null;
    }

    private String safeError(Throwable ex) {
        String s = ex.getMessage();
        if (s == null || s.isBlank()) s = ex.getClass().getSimpleName();
        s = s.replaceAll("[\\r\\n\\t]", " ");
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
