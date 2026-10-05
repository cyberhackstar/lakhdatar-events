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
        var candidates = payments.findReconciliationCandidates(states,cutoff,windowStart,recheckBefore,PageRequest.of(0,100));
        EnterpriseLog.debug(log, "payment.reconciliation.sweep.started", "event.category", "recovery", "batch.size", candidates.size());
        int failed = 0;
        for(Payment p:candidates){
            try{
                if(p.getProviderOrderId()==null) continue;
                var providerPayments = gateways.forPayment(p).fetchPaymentsForOrder(p.getProviderOrderId());
                var provider = orders.selectProviderPaymentForReconciliation(p, providerPayments);
                if(provider.isPresent()){
                    if(orders.reconcileProviderRefundsIfPresent(p.getId(),provider.get())) { /* provider refund is already being reconciled */ }
                    else {var result=orders.reconcileCapturedPayment(p.getId(),provider.get()); if("REFUND_PENDING".equals(result.status())) orders.completeQueuedRefundIfNeeded(p.getId(),"Reservation expired before payment reconciliation");}
                }
            }catch(Exception ex){ failed++; EnterpriseLog.warn(log, "payment.reconciliation.deferred", "event.category", "recovery", "payment.id", p.getId(), "error.type", ex.getClass().getSimpleName());}
            finally{ markReconciledQuietly(p.getId()); }
        }
        var refundCandidates = payments.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List.of(Enums.PaymentStatus.REFUND_PENDING),cutoff);
        for(Payment p:refundCandidates){
            try{orders.completeQueuedRefundIfNeeded(p.getId(),"Payment captured without an active ticket reservation");}catch(Exception ex){ failed++; EnterpriseLog.warn(log, "refund.reconciliation.deferred", "event.category", "recovery", "payment.id", p.getId(), "error.type", ex.getClass().getSimpleName());}
        }
        EnterpriseLog.info(log, "payment.reconciliation.sweep.completed", "event.category", "recovery", "batch.size", candidates.size(), "refund_batch.size", refundCandidates.size(), "failed", failed, "duration.ms", (System.nanoTime()-started)/1_000_000L);
    }
    private void markReconciledQuietly(Long id){
        try{ payments.markReconciled(id,Instant.now()); }catch(Exception ex){
            EnterpriseLog.warn(log, "payment.reconciliation.timestamp_failed", "event.category", "recovery",
                    "payment.id", id, "error.type", ex.getClass().getSimpleName());
        }
    }
}
