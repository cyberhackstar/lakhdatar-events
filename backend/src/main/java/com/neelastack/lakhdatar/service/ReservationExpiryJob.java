package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.repository.TicketReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
@RequiredArgsConstructor
public class ReservationExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final TicketReservationRepository reservations;
    private final TicketReservationService service;

    private final DistributedLockService locks;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${app.reservation.sweep-batch-size:500}") private int batchSize = 500;
    @Value("${app.reservation.sweep-max-batches:8}") private int maxBatches = 8;

    @Scheduled(fixedDelayString = "${app.reservation.sweep}")
    public void sweep() {
        if (!workerEnabled) return;
        locks.withLock("job:reservation-expiry", java.time.Duration.ofSeconds(45), () -> {
            long started = System.nanoTime();
            int failed = 0;
            int processed = 0;
            int batches = 0;
            while (batches < Math.max(1, maxBatches)) {
                var batch = reservations.findByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        Enums.ReservationStatus.HELD, Instant.now(), PageRequest.of(0, Math.max(1, batchSize)));
                if (batch.isEmpty()) break;
                batches++;
                for (var reservation : batch) {
                    try {
                        service.expireReservationAndOrder(reservation.getId());
                        processed++;
                    } catch (Exception ex) {
                        failed++;
                        EnterpriseLog.error(log, "reservation.expiry.failed", ex, "event.category", "recovery", "reservation.id", reservation.getId());
                    }
                }
                if (batch.size() < Math.max(1, batchSize)) break;
            }
            EnterpriseLog.info(log, "reservation.expiry.sweep.completed", "event.category", "recovery", "processed", processed, "batches", batches, "failed", failed, "duration.ms", (System.nanoTime()-started)/1_000_000L);
        });
    }
}
