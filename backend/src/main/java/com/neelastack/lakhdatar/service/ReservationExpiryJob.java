package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.repository.TicketReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
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

    @Scheduled(fixedDelayString = "${app.reservation.sweep}")
    public void sweep() {
        if (!workerEnabled) return;
        locks.withLock("job:reservation-expiry", java.time.Duration.ofSeconds(45), () -> {
            long started = System.nanoTime();
            var batch = reservations.findTop200ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                    Enums.ReservationStatus.HELD, Instant.now());
            EnterpriseLog.debug(log, "reservation.expiry.sweep.started", "event.category", "recovery", "batch.size", batch.size());
            int failed = 0;
            for (var reservation : batch) {
                try {
                    service.expireReservationAndOrder(reservation.getId());
                } catch (Exception ex) {
                    failed++;
                    EnterpriseLog.error(log, "reservation.expiry.failed", ex, "event.category", "recovery", "reservation.id", reservation.getId());
                }
            }
            EnterpriseLog.info(log, "reservation.expiry.sweep.completed", "event.category", "recovery", "batch.size", batch.size(), "failed", failed, "duration.ms", (System.nanoTime()-started)/1_000_000L);
        });
    }
}
