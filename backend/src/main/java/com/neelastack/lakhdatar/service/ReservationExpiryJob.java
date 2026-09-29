package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.repository.TicketReservationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final TicketReservationRepository reservations;
    private final TicketReservationService service;

    @Scheduled(fixedDelayString = "${app.reservation.sweep}")
    public void sweep() {
        var batch = reservations.findTop200ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                Enums.ReservationStatus.HELD, Instant.now());
        for (var reservation : batch) {
            try {
                service.expireReservationAndOrder(reservation.getId());
            } catch (Exception ex) {
                log.error("Reservation expiry failed id={}", reservation.getId(), ex);
            }
        }
    }
}
