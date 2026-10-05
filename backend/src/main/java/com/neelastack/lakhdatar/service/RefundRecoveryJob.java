package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
@RequiredArgsConstructor
public class RefundRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(RefundRecoveryJob.class);
    private final RefundRepository refunds;
    private final RefundService service;

    private final DistributedLockService locks;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;

    @Scheduled(fixedDelayString = "${app.refund.recovery-sweep:30000}")
    public void sweep() {
        if (!workerEnabled) return;
        locks.withLock("job:refund-recovery", java.time.Duration.ofSeconds(55), () -> {
            long started = System.nanoTime();
            var batch = refunds.findTop100ByStatusInOrderByCreatedAtAsc(java.util.List.of(Enums.RefundStatus.REQUESTED, Enums.RefundStatus.PROCESSING));
            EnterpriseLog.debug(log, "refund.recovery.sweep.started", "event.category", "recovery", "batch.size", batch.size());
            int failed = 0;
            for (var refund : batch) {
                try { service.processRefund(refund.getId()); }
                catch (Exception ex) { failed++; EnterpriseLog.warn(log, "refund.recovery.deferred", "event.category", "recovery", "refund.id", refund.getId(), "error.type", ex.getClass().getSimpleName()); }
            }
            EnterpriseLog.info(log, "refund.recovery.sweep.completed", "event.category", "recovery", "batch.size", batch.size(), "failed", failed, "duration.ms", (System.nanoTime()-started)/1_000_000L);
        });
    }
}
