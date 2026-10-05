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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

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
        EnterpriseLog.debug(log, "webhook.received", "event.category", "payment", "provider", "RAZORPAY", "webhook.event.id", providerEventId, "webhook.type", eventType);
        PaymentWebhookEvent stored = events.findByProviderEventId(providerEventId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_PERSISTENCE_FAILED", "Webhook could not be persisted"));
        if (stored.isProcessed()) { EnterpriseLog.debug(log, "webhook.duplicate_ignored", "event.category", "payment", "webhook.event.id", providerEventId, "webhook.type", eventType); return; }
        if (claim(providerEventId) == 0) { EnterpriseLog.debug(log, "webhook.concurrent_delivery_ignored", "event.category", "payment", "webhook.event.id", providerEventId); return; }
        try {
            process(stored.getPayload());
            tx.executeWithoutResult(s -> events.markProcessed(providerEventId, Instant.now()));
            EnterpriseLog.info(log, "webhook.processed", "event.category", "payment", "webhook.event.id", providerEventId, "webhook.type", eventType, "duration.ms", (System.nanoTime()-started)/1_000_000L);
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(s -> events.markFailed(providerEventId, safeError(ex)));
            EnterpriseLog.error(log, "webhook.processing.failed", ex, "event.category", "payment", "webhook.event.id", providerEventId, "webhook.type", eventType, "error.type", ex.getClass().getSimpleName(), "duration.ms", (System.nanoTime()-started)/1_000_000L);
            throw ex;
        }
    }

    private void persistIfAbsent(String eventId, String type, String raw, String hash) {
        try {
            tx.executeWithoutResult(s -> {
                if (events.findByProviderEventId(eventId).isEmpty()) {
                    PaymentWebhookEvent e = new PaymentWebhookEvent();
                    e.setProviderEventId(eventId);
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

    private int claim(String id) {
        Integer result = tx.execute(s -> events.claimForProcessing(id, Instant.now()));
        return result == null ? 0 : result;
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

    @ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Scheduled(fixedDelayString = "${app.razorpay.webhook-recovery-sweep:60000}")
    void recoverStaleProcessing() {
        if (!workerEnabled) return;
        locks.withLock("job:webhook-recovery", Duration.ofMinutes(2), () -> {
            int reset = events.resetStaleProcessing(Instant.now().minus(Duration.ofMinutes(10)));
            if (reset > 0) {
                EnterpriseLog.warn(log, "webhook.stale_claims_reset", "event.category", "payment", "webhook.reset_count", reset);
            }
        });
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
