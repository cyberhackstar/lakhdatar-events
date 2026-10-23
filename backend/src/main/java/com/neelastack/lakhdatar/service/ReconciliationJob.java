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

@Component
@ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
@RequiredArgsConstructor
public class ReconciliationJob {
    private static final Logger log=LoggerFactory.getLogger(ReconciliationJob.class);
    private final PaymentRepository payments; private final PaymentGatewayRouter gateways; private final OrderService orders; private final AppProperties props; private final DistributedLockService locks;
    // Sweeps only look back this far and revisit a payment at most once per recheck interval, so
    // abandoned checkouts can never crowd newer payments out of the batch.
    @Value("${app.payment.reconciliation-window-hours:168}") private long windowHours = 168;
    @Value("${app.payment.reconciliation-recheck-ms:60000}") private long recheckMs = 60_000;
    @Value("${app.payment.reconciliation-batch-size:40}") private int batchSize = 40;
    @Value("${app.payment.reconciliation-backlog-window-hours:2160}") private long backlogWindowHours = 2160;
    @Value("${app.payment.reconciliation-backlog-batch-size:20}") private int backlogBatchSize = 20;
    @Value("${app.payment.reconciliation-backlog-recheck-ms:900000}") private long backlogRecheckMs = 900_000;
    @Scheduled(fixedDelayString="${app.payment.reconciliation-sweep-ms:30000}")
    public void reconcile(){
        locks.withLock("job:payment-reconciliation", Duration.ofSeconds(55), this::reconcileLocked);
    }
    private void reconcileLocked(){
        long started = System.nanoTime();
        Instant cutoff=Instant.now().minusMillis(props.payment().reconciliationAgeMs());
        List<Enums.PaymentStatus> states=List.of(Enums.PaymentStatus.CREATED,Enums.PaymentStatus.PENDING,Enums.PaymentStatus.PAYMENT_INITIATED,Enums.PaymentStatus.AUTHORIZED,Enums.PaymentStatus.CAPTURED,Enums.PaymentStatus.COMPLETED);
        Instant now=Instant.now();
        Instant windowStart=now.minus(Duration.ofHours(Math.max(1,windowHours)));
        Instant recheckBefore=now.minusMillis(Math.max(0,recheckMs));
        var candidates = payments.findReconciliationCandidates(states,cutoff,windowStart,recheckBefore,
                PageRequest.of(0,Math.max(1,batchSize)));
        Instant backlogWindowStart = now.minus(Duration.ofHours(Math.max(168, backlogWindowHours)));
        Instant backlogRecheckBefore = now.minusMillis(Math.max(0, backlogRecheckMs));
        var backlogCandidates = payments.findHistoricalClosedOrderReconciliationCandidates(
                states, List.of(Enums.OrderStatus.EXPIRED, Enums.OrderStatus.CANCELLED), cutoff,
                windowStart, backlogWindowStart, backlogRecheckBefore,
                PageRequest.of(0, Math.max(1, backlogBatchSize)));
        java.util.LinkedHashMap<Long, Payment> uniqueCandidates = new java.util.LinkedHashMap<>();
        candidates.forEach(p -> uniqueCandidates.put(p.getId(), p));
        backlogCandidates.forEach(p -> uniqueCandidates.putIfAbsent(p.getId(), p));
        candidates = java.util.List.copyOf(uniqueCandidates.values());
        EnterpriseLog.debug(log, "payment.reconciliation.sweep.started", "event.category", "recovery",
                "batch.size", candidates.size(), "backlog.batch.size", backlogCandidates.size());
        int failed = 0;
        int attempted = 0;
        for(Payment p:candidates){
            try{
                if(p.getProviderOrderId()==null) continue;
                attempted++;
                var gateway = gateways.forPayment(p);
                var providerPayments = gateway.fetchPaymentsForOrder(p.getProviderOrderId());
                var provider = orders.selectProviderPaymentForReconciliation(p, providerPayments);
                if(provider.isPresent()){
                    if(orders.reconcileProviderRefundsIfPresent(p.getId(),provider.get())) { /* provider refund is already being reconciled */ }
                    else {var result=orders.reconcileCapturedPayment(p.getId(),provider.get()); if("REFUND_PENDING".equals(result.status())) orders.completeQueuedRefundIfNeeded(p.getId(),"Reservation expired before payment reconciliation");}
                } else if (isTerminalAttempts(providerPayments)) {
                    // A terminal payment attempt alone is not enough: independently confirm the
                    // provider order itself is terminal before changing local payment/order state.
                    var providerOrder = gateway.fetchOrder(p.getProviderOrderId());
                    orders.reconcileTerminalUncapturedPayment(p.getId(), providerOrder, providerPayments);
                }
            }catch(Exception ex){ failed++; EnterpriseLog.warn(log, "payment.reconciliation.deferred", "event.category", "recovery", "payment.id", p.getId(), "error.type", ex.getClass().getSimpleName());}
            finally{ markAttemptedQuietly(p.getId()); }
        }
        var refundCandidates = payments.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List.of(Enums.PaymentStatus.REFUND_PENDING),cutoff);
        for(Payment p:refundCandidates){
            try{orders.completeQueuedRefundIfNeeded(p.getId(),"Payment captured without an active ticket reservation");}catch(Exception ex){ failed++; EnterpriseLog.warn(log, "refund.reconciliation.deferred", "event.category", "recovery", "payment.id", p.getId(), "error.type", ex.getClass().getSimpleName());}
        }
        EnterpriseLog.info(log, "payment.reconciliation.sweep.completed", "event.category", "recovery",
                "batch.size", candidates.size(), "backlog.batch.size", backlogCandidates.size(),
                "attempted", attempted, "refund_batch.size", refundCandidates.size(), "failed", failed,
                "duration.ms", (System.nanoTime()-started)/1_000_000L);
    }
    // Historical column name retained for schema compatibility; this records the last attempt,
    // not proof of success. Empty/failed/transient provider results are never considered terminal.
    private void markAttemptedQuietly(Long id) {
        try { payments.markReconciled(id, Instant.now()); }
        catch (Exception ex) {
            EnterpriseLog.warn(log, "payment.reconciliation.attempt_timestamp_failed", "event.category", "recovery",
                    "payment.id", id, "error.type", ex.getClass().getSimpleName());
        }
    }

    private boolean isTerminalAttempts(List<com.neelastack.lakhdatar.service.PaymentGatewayProvider.ProviderPayment> payments) {
        if (payments == null || payments.isEmpty()) return false;
        return payments.stream().allMatch(p -> {
            if (p == null || p.captured() || p.amountRefunded() > 0 || p.status() == null) return false;
            String state = p.status().trim().toLowerCase(java.util.Locale.ROOT);
            return java.util.Set.of("failed", "cancelled", "canceled", "user_dropped", "user dropped").contains(state);
        });
    }
}
