package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.domain.TicketType;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import com.neelastack.lakhdatar.repository.TicketReservationRepository;
import com.neelastack.lakhdatar.repository.TicketTypeRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class TicketReservationConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired private TicketReservationService service;
    @Autowired private OrganizerRepository organizers;
    @Autowired private EventRepository events;
    @Autowired private TicketTypeRepository types;
    @Autowired private TicketReservationRepository reservations;

    @Test
    void cannotOversellLastTwoTicketsUnderConcurrency() throws Exception {
        Organizer organizer = new Organizer();
        organizer.setName("Test Organizer");
        organizer.setSlug("test-organizer-reservation");
        organizer = organizers.saveAndFlush(organizer);

        Event event = new Event();
        event.setOrganizerId(organizer.getId());
        event.setName("Concurrency Event");
        event.setSlug("reservation-concurrency-test");
        event.setStartsAt(Instant.now().plusSeconds(86_400));
        event.setCurrency("INR");
        events.saveAndFlush(event);

        TicketType type = new TicketType();
        type.setEventId(event.getId());
        type.setName("VIP");
        type.setPriceMinorUnits(10000L);
        type.setCurrency("INR");
        type.setTotalQuantity(2);
        type.setMinPerOrder(1);
        type.setMaxPerOrder(1);
        type = types.saveAndFlush(type);

        Long typeId = type.getId();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    service.reserve(typeId, 1);
                    return true;
                } catch (ApiException ex) {
                    return false;
                }
            }));
        }
        start.countDown();

        AtomicInteger success = new AtomicInteger();
        for (Future<Boolean> future : futures) if (future.get(30, TimeUnit.SECONDS)) success.incrementAndGet();
        pool.shutdown();

        assertEquals(2, success.get());
        TicketType after = types.findById(typeId).orElseThrow();
        assertEquals(2, after.getReservedQuantity());
        assertEquals(2, after.getReservedQuantity() + after.getSoldQuantity());
    }
}
