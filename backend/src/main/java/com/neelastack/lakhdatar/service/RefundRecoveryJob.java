package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

@Component
@ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
@RequiredArgsConstructor
public class RefundRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(RefundRecoveryJob.class);
    private final RefundRepository refunds;
    private final RefundService service;

    private final DistributedLockService locks;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${app.refund.processing-timeout:24h}") private Duration processingTimeout;
    @Value("${app.refund.max-attempts:8}") private int maxAttempts;
    @Value("${app.refund.recovery-batch-size:500}") private int batchSize;
    @Value("${app.refund.recovery-max-batches:4}") private int maxBatches;

    @Scheduled(fixedDelayString = "${app.refund.recovery-sweep:30000}")
    public void sweep() {
        if (!workerEnabled) return;
        locks.withLock("job:refund-recovery", java.time.Duration.ofSeconds(55), () -> {
            long started = System.nanoTime();
            Instant now = Instant.now();
            var statuses = java.util.List.of(Enums.RefundStatus.REQUESTED, Enums.RefundStatus.PROCESSING);
            int failed = 0, processed = 0, batches = 0;
            while (batches++ < Math.max(1, maxBatches)) {
                var batch = refunds.findByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscCreatedAtAsc(
                        statuses, now, PageRequest.of(0, Math.max(1, batchSize)));
                if (batch.isEmpty()) break;
                EnterpriseLog.debug(log, "refund.recovery.batch", "event.category", "recovery", "batch.size", batch.size(), "batch.number", batches);
                for (var refund : batch) {
                try {
                    if (refund.getAttemptCount() >= Math.max(1, maxAttempts)
                            || (refund.getCreatedAt() != null && refund.getCreatedAt().plus(processingTimeout).isBefore(now))) {
                        service.markManualReviewRequired(refund.getId(),
                                refund.getAttemptCount() >= Math.max(1, maxAttempts) ? "Maximum automatic refund attempts reached" : "Refund remained recoverable for longer than the configured processing timeout");
                        continue;
                    }
                    service.processRefund(refund.getId());
                }
                catch (Exception ex) { failed++; EnterpriseLog.warn(log, "refund.recovery.deferred", "event.category", "recovery", "refund.id", refund.getId(), "error.type", ex.getClass().getSimpleName()); }
                processed++;
                }
            }
            EnterpriseLog.info(log, "refund.recovery.sweep.completed", "event.category", "recovery", "processed", processed, "batches", Math.min(batches, Math.max(1, maxBatches)), "failed", failed, "duration.ms", (System.nanoTime()-started)/1_000_000L);
        });
    }
}
