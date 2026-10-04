package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.domain.TicketType;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import com.neelastack.lakhdatar.repository.TicketTypeRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class EventInventoryConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired EventManagementService eventManagement;
    @Autowired OrganizerRepository organizers;
    @Autowired EventRepository events;
    @Autowired TicketTypeRepository ticketTypes;
    @Autowired UserRepository users;

    @Test
    void concurrentTicketTypeAddsNeverExceedEventCapacity() throws Exception {
        Organizer organizer = new Organizer();
        organizer.setName("Capacity Concurrency Organizer " + UUID.randomUUID());
        organizer.setSlug("capacity-concurrency-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        organizer = organizers.saveAndFlush(organizer);

        User admin = new User();
        admin.setEmail("capacity-admin-" + UUID.randomUUID() + "@example.com");
        admin.setFullName("Capacity Admin");
        admin.setPasswordHash("$2a$10$placeholderhashplaceholderhashplaceholderhashplaceholderhash");
        admin.setRole(Enums.UserRole.ADMIN);
        admin.setEnabled(true);
        admin = users.saveAndFlush(admin);

        Event event = new Event();
        event.setSlug("capacity-event-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        event.setOrganizerId(organizer.getId());
        event.setName("Capacity Concurrency Event");
        event.setStartsAt(Instant.now().plusSeconds(3600));
        event.setStatus(Enums.EventStatus.DRAFT);
        event.setCapacity(100);
        event = events.saveAndFlush(event);

        TicketType base = new TicketType();
        base.setEventId(event.getId());
        base.setName("Base");
        base.setPriceMinorUnits(10000L);
        base.setCurrency("INR");
        base.setTotalQuantity(60);
        base.setMinPerOrder(1);
        base.setMaxPerOrder(20);
        base.setStatus(Enums.TicketTypeStatus.ACTIVE);
        ticketTypes.saveAndFlush(base);

        UUID publicId = event.getPublicId();
        Long actorId = admin.getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    eventManagement.addTicketType(publicId,
                            new EventManagementService.CreateTicketType(
                                    "Concurrent " + UUID.randomUUID(), null, 10000L, 30, 1, 20, null, null),
                            actorId, "ADMIN");
                    return true;
                } catch (RuntimeException expectedCapacityRejection) {
                    return false;
                }
            }));
        }
        start.countDown();

        int successfulAdds = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) successfulAdds++;
        }
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        long configured = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(event.getId()).stream()
                .mapToLong(TicketType::getTotalQuantity).sum();
        assertEquals(1, successfulAdds, "Exactly one concurrent 30-seat inventory change may succeed");
        assertTrue(configured <= 100, "event-wide configured inventory must never exceed capacity");
    }
}
