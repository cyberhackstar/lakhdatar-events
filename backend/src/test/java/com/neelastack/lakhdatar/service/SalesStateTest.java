package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests for the derived customer-facing sales state and booking window. */
class SalesStateTest {
    private final Instant now = Instant.parse("2026-10-01T10:00:00Z");

    private Event event(Enums.EventStatus status, long startOffsetDays) {
        Event e = new Event();
        e.setStatus(status);
        e.setStartsAt(now.plusSeconds(startOffsetDays * 86_400));
        return e;
    }

    @Test void availableWhenStockPlentiful() {
        assertEquals("AVAILABLE", PublicEventService.salesState(event(Enums.EventStatus.PUBLISHED, 5), true, 500, 1000, now));
    }
    @Test void sellingFastAtTwentyPercentOrLess() {
        assertEquals("SELLING_FAST", PublicEventService.salesState(event(Enums.EventStatus.PUBLISHED, 5), true, 200, 1000, now));
        assertEquals("AVAILABLE", PublicEventService.salesState(event(Enums.EventStatus.PUBLISHED, 5), true, 201, 1000, now));
    }
    @Test void soldOutWhenNothingLeftOrNoActiveTypes() {
        assertEquals("SOLD_OUT", PublicEventService.salesState(event(Enums.EventStatus.PUBLISHED, 5), true, 0, 1000, now));
        assertEquals("SOLD_OUT", PublicEventService.salesState(event(Enums.EventStatus.PUBLISHED, 5), false, 0, 0, now));
    }
    @Test void cancelledAndCompletedStatusWinOverInventory() {
        assertEquals("CANCELLED", PublicEventService.salesState(event(Enums.EventStatus.CANCELLED, 5), true, 500, 1000, now));
        assertEquals("COMPLETED", PublicEventService.salesState(event(Enums.EventStatus.COMPLETED, -5), true, 500, 1000, now));
    }
    @Test void multiDayEventKeepsBookingOpenUntilEventEnd() {
        Event running = event(Enums.EventStatus.PUBLISHED, 0);
        running.setStartsAt(now.minusSeconds(60)); running.setEndsAt(now.plusSeconds(3600));
        assertEquals("AVAILABLE", PublicEventService.salesState(running, true, 500, 1000, now));
        assertTrue(PublicEventService.bookingWindowOpen(running, now));
        running.setEndsAt(now.minusSeconds(10));
        assertEquals("COMPLETED", PublicEventService.salesState(running, true, 500, 1000, now));
        assertFalse(PublicEventService.bookingWindowOpen(running, now));
    }
    @Test void multiDayBookingWindowUsesEndWhenExplicitBookingEndIsAbsent() {
        Event e = event(Enums.EventStatus.PUBLISHED, 0);
        e.setStartsAt(now.minusSeconds(3600));
        e.setEndsAt(now.plusSeconds(3600));
        e.setBookingStartsAt(now.minusSeconds(7200));
        e.setBookingEndsAt(null);
        assertTrue(PublicEventService.bookingWindowOpen(e, now));
        e.setEndsAt(now.minusSeconds(1));
        assertFalse(PublicEventService.bookingWindowOpen(e, now));
    }

    @Test void bookingWindowStatesAreHonoured() {
        Event e = event(Enums.EventStatus.PUBLISHED, 5);
        e.setBookingStartsAt(now.plusSeconds(3600));
        assertEquals("BOOKING_NOT_STARTED", PublicEventService.salesState(e, true, 500, 1000, now));
        assertFalse(PublicEventService.bookingWindowOpen(e, now));
        e.setBookingStartsAt(now.minusSeconds(3600)); e.setBookingEndsAt(now.minusSeconds(1));
        assertEquals("BOOKING_CLOSED", PublicEventService.salesState(e, true, 500, 1000, now));
        assertFalse(PublicEventService.bookingWindowOpen(e, now));
        e.setBookingEndsAt(now.plusSeconds(60));
        assertTrue(PublicEventService.bookingWindowOpen(e, now));
        assertEquals("AVAILABLE", PublicEventService.salesState(e, true, 500, 1000, now));
    }
    @Test void windowIsOpenWhenUnconfigured() {
        assertTrue(PublicEventService.bookingWindowOpen(event(Enums.EventStatus.PUBLISHED, 5), now));
    }
}
