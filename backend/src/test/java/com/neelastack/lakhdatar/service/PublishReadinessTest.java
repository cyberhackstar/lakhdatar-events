package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PublishReadinessTest {
    private static final Instant FUTURE = Instant.now().plusSeconds(3600);
    private static final Instant END = FUTURE.plusSeconds(7200);

    @Test
    void readyEventPassesAllPublicationGates() {
        var r = PublishReadiness.evaluate("ADMIN", Enums.EventStatus.DRAFT, FUTURE, END,
                null, END, true, true, true);
        assertTrue(r.ready());
        assertTrue(r.blockers().isEmpty());
    }

    @Test
    void missingTicketBlocksPublication() {
        var r = PublishReadiness.evaluate("ADMIN", Enums.EventStatus.DRAFT, FUTURE, END,
                null, END, false, false, true);
        assertFalse(r.ready());
        assertTrue(r.blockers().stream().anyMatch(x -> x.contains("ticket type")));
    }

    @Test
    void pausedTicketsBlockPublication() {
        var r = PublishReadiness.evaluate("ADMIN", Enums.EventStatus.DRAFT, FUTURE, END,
                null, END, true, false, true);
        assertFalse(r.ready());
        assertTrue(r.blockers().stream().anyMatch(x -> x.contains("must be ACTIVE")));
    }

    @Test
    void nonManagerCannotPublish() {
        var r = PublishReadiness.evaluate("EVENT_MANAGER", Enums.EventStatus.DRAFT, FUTURE, END,
                null, END, true, true, true);
        assertFalse(r.ready());
        assertTrue(r.blockers().get(0).contains("administrators or organizer owners"));
    }

    @Test
    void alreadyPublishedIsIdempotentlyReady() {
        var r = PublishReadiness.evaluate("ADMIN", Enums.EventStatus.PUBLISHED, FUTURE, END,
                null, END, false, false, false);
        assertTrue(r.ready());
    }
}
