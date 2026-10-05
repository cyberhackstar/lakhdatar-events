package com.neelastack.lakhdatar.service;

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
            var batch = reservations.findTop200ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                    Enums.ReservationStatus.HELD, Instant.now());
            for (var reservation : batch) {
                try {
                    service.expireReservationAndOrder(reservation.getId());
                } catch (Exception ex) {
                    log.error("Reservation expiry failed id={}", reservation.getId(), ex);
                }
            }
        });
    }
}
