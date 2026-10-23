package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private static final List<Enums.PaymentStatus> RECONCILABLE_STATES = List.of(
            Enums.PaymentStatus.CREATED,
            Enums.PaymentStatus.PENDING,
            Enums.PaymentStatus.PAYMENT_INITIATED,
            Enums.PaymentStatus.AUTHORIZED,
            Enums.PaymentStatus.CAPTURED,
            Enums.PaymentStatus.COMPLETED);
    private static final List<Enums.OrderStatus> CLOSED_ORDER_STATES = List.of(
            Enums.OrderStatus.EXPIRED, Enums.OrderStatus.CANCELLED);

    private final PaymentRepository payments;
    private final PaymentGatewayRouter gateways;
    private final OrderService orders;
    private final AppProperties props;
    private final DistributedLockService locks;

    @Value("${app.payment.reconciliation-window-hours:168}")
    private long windowHours = 168;
    @Value("${app.payment.reconciliation-recheck-ms:60000}")
    private long recheckMs = 60_000;
    @Value("${app.payment.reconciliation-batch-size:40}")
    private int batchSize = 40;
    @Value("${app.payment.reconciliation-backlog-window-hours:2160}")
    private long backlogWindowHours = 2160;
    @Value("${app.payment.reconciliation-backlog-batch-size:20}")
    private int backlogBatchSize = 20;
    @Value("${app.payment.reconciliation-backlog-recheck-ms:900000}")
    private long backlogRecheckMs = 900_000;

    /** Recent payments stay on the fast reconciliation lane. */
    @Scheduled(fixedDelayString = "${app.payment.reconciliation-sweep-ms:30000}")
    public void reconcile() {
        // Both lanes share one distributed lock so historical provider calls cannot overlap
        // with current-checkout reconciliation on another scheduler thread or application node.
        locks.withLock("job:payment-reconciliation", Duration.ofMinutes(5), this::reconcileRecentLocked);
    }

    /** Closed orders older than the recent window are intentionally processed at a lower rate. */
    @Scheduled(fixedDelayString = "${app.payment.reconciliation-backlog-sweep-ms:120000}")
    public void reconcileHistoricalClosedOrders() {
        locks.withLock("job:payment-reconciliation", Duration.ofMinutes(5), this::reconcileHistoricalClosedOrdersLocked);
    }

    private void reconcileRecentLocked() {
        long started = System.nanoTime();
        Instant now = Instant.now();
        Instant cutoff = now.minusMillis(Math.max(0, props.payment().reconciliationAgeMs()));
        Instant windowStart = now.minus(Duration.ofHours(Math.max(1, windowHours)));
        Instant recheckBefore = now.minusMillis(Math.max(0, recheckMs));

        var candidates = payments.findReconciliationCandidates(
                RECONCILABLE_STATES, cutoff, windowStart, recheckBefore,
                PageRequest.of(0, Math.max(1, batchSize)));
        int failed = processCandidates(candidates, "recent");

        var refundCandidates = payments.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(Enums.PaymentStatus.REFUND_PENDING), cutoff);
        int refundFailed = 0;
        for (Payment payment : refundCandidates) {
            try {
                orders.completeQueuedRefundIfNeeded(payment.getId(),
                        "Payment captured without an active ticket reservation");
            } catch (Exception ex) {
                refundFailed++;
                EnterpriseLog.warn(log, "refund.reconciliation.deferred",
                        "event.category", "recovery", "payment.id", payment.getId(),
                        "error.type", ex.getClass().getSimpleName());
            }
        }
        EnterpriseLog.info(log, "payment.reconciliation.sweep.completed",
                "event.category", "recovery", "lane", "recent",
                "batch.size", candidates.size(), "refund_batch.size", refundCandidates.size(),
                "failed", failed + refundFailed,
                "duration.ms", (System.nanoTime() - started) / 1_000_000L);
    }

    private void reconcileHistoricalClosedOrdersLocked() {
        long started = System.nanoTime();
        Instant now = Instant.now();
        Instant cutoff = now.minusMillis(Math.max(0, props.payment().reconciliationAgeMs()));
        Instant recentWindowStart = now.minus(Duration.ofHours(Math.max(1, windowHours)));
        Instant backlogWindowStart = now.minus(Duration.ofHours(Math.max(168, backlogWindowHours)));
        Instant recheckBefore = now.minusMillis(Math.max(0, backlogRecheckMs));

        var candidates = payments.findHistoricalClosedOrderReconciliationCandidates(
                RECONCILABLE_STATES, CLOSED_ORDER_STATES, cutoff, recentWindowStart,
                backlogWindowStart, recheckBefore,
                PageRequest.of(0, Math.max(1, backlogBatchSize)));
        int failed = processCandidates(candidates, "historical-closed-order");
        EnterpriseLog.info(log, "payment.reconciliation.sweep.completed",
                "event.category", "recovery", "lane", "historical-closed-order",
                "batch.size", candidates.size(), "failed", failed,
                "duration.ms", (System.nanoTime() - started) / 1_000_000L);
    }

    private int processCandidates(List<Payment> candidates, String lane) {
        int failed = 0;
        int attempted = 0;
        for (Payment payment : candidates) {
            try {
                if (payment.getProviderOrderId() == null) continue;
                attempted++;
                var gateway = gateways.forPayment(payment);
                var providerPayments = gateway.fetchPaymentsForOrder(payment.getProviderOrderId());
                var providerPayment = orders.selectProviderPaymentForReconciliation(payment, providerPayments);
                if (providerPayment.isPresent()) {
                    if (orders.reconcileProviderRefundsIfPresent(payment.getId(), providerPayment.get())) {
                        // Provider refund is already being reconciled.
                    } else {
                        var result = orders.reconcileCapturedPayment(payment.getId(), providerPayment.get());
                        if ("REFUND_PENDING".equals(result.status())) {
                            orders.completeQueuedRefundIfNeeded(payment.getId(),
                                    "Reservation expired before payment reconciliation");
                        }
                    }
                } else if (isTerminalAttempts(providerPayments)) {
                    // A terminal attempt alone is insufficient: independently confirm the provider
                    // order is terminal before changing local payment/order state.
                    var providerOrder = gateway.fetchOrder(payment.getProviderOrderId());
                    orders.reconcileTerminalUncapturedPayment(payment.getId(), providerOrder, providerPayments);
                }
            } catch (Exception ex) {
                failed++;
                EnterpriseLog.warn(log, "payment.reconciliation.deferred",
                        "event.category", "recovery", "lane", lane,
                        "payment.id", payment.getId(), "error.type", ex.getClass().getSimpleName());
            } finally {
                markAttemptedQuietly(payment.getId());
            }
        }
        EnterpriseLog.debug(log, "payment.reconciliation.batch.completed",
                "event.category", "recovery", "lane", lane,
                "batch.size", candidates.size(), "attempted", attempted, "failed", failed);
        return failed;
    }

    // Historical column name retained for schema compatibility; this records the last attempt,
    // not proof of successful reconciliation. Unknown/transient results remain unresolved.
    private void markAttemptedQuietly(Long id) {
        try {
            payments.markReconciled(id, Instant.now());
        } catch (Exception ex) {
            EnterpriseLog.warn(log, "payment.reconciliation.attempt_timestamp_failed",
                    "event.category", "recovery", "payment.id", id,
                    "error.type", ex.getClass().getSimpleName());
        }
    }

    private boolean isTerminalAttempts(List<PaymentGatewayProvider.ProviderPayment> providerPayments) {
        if (providerPayments == null || providerPayments.isEmpty()) return false;
        return providerPayments.stream().allMatch(payment -> {
            if (payment == null || payment.captured() || payment.amountRefunded() > 0 || payment.status() == null) return false;
            String state = payment.status().trim().toLowerCase(Locale.ROOT);
            return Set.of("failed", "cancelled", "canceled", "user_dropped", "user dropped").contains(state);
        });
    }
}
