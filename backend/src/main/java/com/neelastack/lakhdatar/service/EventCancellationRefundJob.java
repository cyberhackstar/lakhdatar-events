package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts event cancellation into durable, retryable refund work without blocking on Razorpay. */
@Component
@ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
@RequiredArgsConstructor
public class EventCancellationRefundJob {
    private static final Logger log = LoggerFactory.getLogger(EventCancellationRefundJob.class);
    private final PaymentRepository payments;
    private final RefundService refunds;

    private final DistributedLockService locks;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${app.refund.max-attempts:8}") private int maxAttempts;

    @Scheduled(fixedDelayString = "${app.refund.cancellation-sweep:30000}")
    public void sweep() {
        if (!workerEnabled) return;
        locks.withLock("job:event-cancellation-refunds", java.time.Duration.ofSeconds(55), this::sweepLocked);
    }
    private void sweepLocked() {
        long started = System.nanoTime();
        var statuses = List.of(Enums.PaymentStatus.CAPTURED.name(), Enums.PaymentStatus.COMPLETED.name(), Enums.PaymentStatus.REFUND_PENDING.name());
        var candidates = payments.findCancelledEventRefundCandidates(
                Enums.EventStatus.CANCELLED.name(), statuses, Math.max(1, maxAttempts), PageRequest.of(0, 100));
        EnterpriseLog.debug(log, "refund.event_cancellation.sweep.started", "event.category", "recovery", "batch.size", candidates.size());
        int failed = 0;
        for (var payment : candidates) {
            try {
                refunds.queueCapturedPaymentRefundOnly(payment.getId(), "Event cancellation");
            } catch (Exception ex) {
                failed++; EnterpriseLog.warn(log, "refund.event_cancellation.queue_deferred", "event.category", "recovery", "payment.id", payment.getId(), "error.type", ex.getClass().getSimpleName());
            }
        }
        EnterpriseLog.info(log, "refund.event_cancellation.sweep.completed", "event.category", "recovery", "batch.size", candidates.size(), "failed", failed, "duration.ms", (System.nanoTime()-started)/1_000_000L);
    }
}
