package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Event isolation, visibility and catalogue filtering against a real PostgreSQL. */
@Tag("integration")
@Import(IntegrationPaymentGatewayConfiguration.class)
class MultiEventCatalogIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private PublicEventService publicEvents;
    @Autowired private EventManagementService management;
    @Autowired private OrganizerRepository organizers;
    @Autowired private UserRepository users;
    @Autowired private EventRepository events;
    @Autowired private TicketTypeRepository types;
    @Autowired private VenueRepository venues;
    @Autowired private TicketReservationService reservations;

    
    private Organizer organizer(String prefix) {
        Organizer o = new Organizer();
        o.setName(prefix + " Org");
        o.setSlug(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        return organizers.saveAndFlush(o);
    }

    private Event event(Organizer o, String slugPrefix, Enums.EventStatus status, int startDays, String city, String category) {
        Venue v = new Venue();
        v.setOrganizerId(o.getId()); v.setName("Hall"); v.setCity(city);
        v = venues.saveAndFlush(v);
        Event e = new Event();
        e.setOrganizerId(o.getId()); e.setVenueId(v.getId());
        e.setName(slugPrefix); e.setSlug(slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStartsAt(Instant.now().plusSeconds(startDays * 86_400L));
        e.setStatus(status); e.setCurrency("INR"); e.setCategory(category); e.setPaymentProvider(Enums.PaymentProvider.RAZORPAY);
        return events.saveAndFlush(e);
    }

    private TicketType type(Event e, String name, long price, int qty) {
        TicketType t = new TicketType();
        t.setEventId(e.getId()); t.setName(name); t.setPriceMinorUnits(price); t.setCurrency("INR");
        t.setTotalQuantity(qty); t.setMinPerOrder(1); t.setMaxPerOrder(5);
        return types.saveAndFlush(t);
    }

    private PublicEventService.Filter byOrganizer(Organizer o) {
        return new PublicEventService.Filter(null, null, null, o.getSlug(), null, null, null, null, null);
    }

    @Test void listingShowsOnlyPublishedEventsOfTheRequestedOrganizer() {
        Organizer a = organizer("iso-a"), b = organizer("iso-b");
        Event pub = event(a, "pub", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        event(a, "draft", Enums.EventStatus.DRAFT, 6, "Jaipur", "Music");
        event(a, "unpub", Enums.EventStatus.UNPUBLISHED, 7, "Jaipur", "Music");
        event(a, "arch", Enums.EventStatus.ARCHIVED, 8, "Jaipur", "Music");
        event(b, "other", Enums.EventStatus.PUBLISHED, 5, "Delhi", "Music");
        type(pub, "General", 50_000, 10);

        var page = publicEvents.list(byOrganizer(a), 0, 20);
        assertEquals(1, page.total());
        assertEquals(pub.getSlug(), page.items().get(0).slug());
        assertEquals(50_000L, page.items().get(0).startingPriceMinor());
    }

    @Test void draftUnpublishedAndArchivedDetailPagesAre404ButCancelledIsViewable() {
        Organizer o = organizer("vis");
        Event draft = event(o, "draft", Enums.EventStatus.DRAFT, 5, "Jaipur", "Music");
        Event unpub = event(o, "unpub", Enums.EventStatus.UNPUBLISHED, 5, "Jaipur", "Music");
        Event arch = event(o, "arch", Enums.EventStatus.ARCHIVED, 5, "Jaipur", "Music");
        Event cancelled = event(o, "cx", Enums.EventStatus.CANCELLED, 5, "Jaipur", "Music");
        for (Event e : List.of(draft, unpub, arch)) {
            ApiException ex = assertThrows(ApiException.class, () -> publicEvents.getBySlug(e.getSlug()));
            assertEquals(404, ex.status().value());
        }
        assertEquals("CANCELLED", publicEvents.getBySlug(cancelled.getSlug()).salesState());
        assertEquals(404, assertThrows(ApiException.class, () -> publicEvents.getBySlug("no-such-slug")).status().value());
    }

    @Test void filtersByCityCategoryAndPriceAreAppliedOnTheServer() {
        Organizer o = organizer("flt");
        Event cheap = event(o, "cheap", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        Event dear = event(o, "dear", Enums.EventStatus.PUBLISHED, 6, "Delhi", "Comedy");
        type(cheap, "GA", 30_000, 10); type(dear, "GA", 900_000, 10);

        var jaipur = publicEvents.list(new PublicEventService.Filter(null, null, "jaipur", o.getSlug(), null, null, null, null, null), 0, 20);
        assertEquals(List.of(cheap.getSlug()), jaipur.items().stream().map(PublicEventService.EventCard::slug).toList());
        var comedy = publicEvents.list(new PublicEventService.Filter(null, "COMEDY", null, o.getSlug(), null, null, null, null, null), 0, 20);
        assertEquals(List.of(dear.getSlug()), comedy.items().stream().map(PublicEventService.EventCard::slug).toList());
        var affordable = publicEvents.list(new PublicEventService.Filter(null, null, null, o.getSlug(), null, null, null, null, 50_000L), 0, 20);
        assertEquals(1, affordable.total());
    }

    @Test void searchTreatsLikeWildcardsAsLiteralText() {
        Organizer o = organizer("wild");
        event(o, "alpha", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        var r = publicEvents.list(new PublicEventService.Filter("%", null, null, o.getSlug(), null, null, null, null, null), 0, 20);
        assertEquals(0, r.total());
    }

    @Test void paginationIsServerSide() {
        Organizer o = organizer("pg");
        for (int i = 0; i < 5; i++) event(o, "e" + i, Enums.EventStatus.PUBLISHED, 5 + i, "Jaipur", "Music");
        var p0 = publicEvents.list(byOrganizer(o), 0, 2);
        var p2 = publicEvents.list(byOrganizer(o), 2, 2);
        assertEquals(5, p0.total()); assertEquals(3, p0.totalPages());
        assertEquals(2, p0.items().size()); assertEquals(1, p2.items().size());
    }

    @Test void soldOutIsDerivedFromInventoryAndPastEventsAreNotListed() {
        Organizer o = organizer("so");
        Event e = event(o, "sold", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        TicketType t = type(e, "GA", 10_000, 1);
        reservations.reserve(t.getId(), 1);
        assertEquals("SOLD_OUT", publicEvents.getBySlug(e.getSlug()).salesState());
        event(o, "past", Enums.EventStatus.PUBLISHED, -3, "Jaipur", "Music");
        assertEquals(1, publicEvents.list(byOrganizer(o), 0, 20).total());
    }

    @Test void reservingOneEventNeverConsumesAnotherEventsInventory() {
        Organizer o = organizer("inv");
        Event a = event(o, "a", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        Event b = event(o, "b", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        TicketType ta = type(a, "GA", 10_000, 10), tb = type(b, "GA", 10_000, 10);
        reservations.reserve(ta.getId(), 4);
        assertEquals(4, types.findById(ta.getId()).orElseThrow().getReservedQuantity());
        assertEquals(0, types.findById(tb.getId()).orElseThrow().getReservedQuantity());
    }

    @Test void memberOfOneOrganizerCannotManageAnotherOrganizersEvent() {
        Organizer a = organizer("authz-a"), b = organizer("authz-b");
        Event eb = event(b, "b", Enums.EventStatus.DRAFT, 5, "Jaipur", "Music");
        type(eb, "GA", 10_000, 10);
        // Actor 999999 belongs to no organizer at all.
        ApiException ex = assertThrows(ApiException.class, () -> management.publish(eb.getPublicId(), 999_999L, "ORGANIZER"));
        assertEquals(403, ex.status().value());
        assertThrows(ApiException.class, () -> management.transition(eb.getPublicId(), EventManagementService.Transition.CANCEL, 999_999L, "EVENT_MANAGER"));
        assertEquals(Enums.EventStatus.DRAFT, events.findById(eb.getId()).orElseThrow().getStatus());
        assertNotNull(a);
    }

    @Test void lifecycleTransitionsAreGuarded() {
        User admin = new User();
        admin.setEmail("lifecycle-admin-" + UUID.randomUUID() + "@test.invalid");
        admin.setPasswordHash("test-only");
        admin.setFullName("Lifecycle Test Admin");
        admin.setRole(Enums.UserRole.ADMIN);
        admin = users.saveAndFlush(admin);
        Long actorId = admin.getId();

        Organizer o = organizer("life");
        Event e = event(o, "life", Enums.EventStatus.DRAFT, 5, "Jaipur", "Music");
        type(e, "GA", 10_000, 10);
        assertThrows(ApiException.class, () -> management.transition(e.getPublicId(), EventManagementService.Transition.UNPUBLISH, actorId, "ADMIN"));
        management.publish(e.getPublicId(), actorId, "ADMIN");
        assertNotNull(events.findById(e.getId()).orElseThrow().getPublishedAt());
        management.transition(e.getPublicId(), EventManagementService.Transition.UNPUBLISH, actorId, "ADMIN");
        management.transition(e.getPublicId(), EventManagementService.Transition.CANCEL, actorId, "ADMIN");
        assertThrows(ApiException.class, () -> management.publish(e.getPublicId(), actorId, "ADMIN"));
        management.transition(e.getPublicId(), EventManagementService.Transition.ARCHIVE, actorId, "ADMIN");
        assertEquals(Enums.EventStatus.ARCHIVED, events.findById(e.getId()).orElseThrow().getStatus());
    }

    @Test void duplicateSlugIsRejectedByTheDatabase() {
        Organizer o = organizer("dup");
        Event e = event(o, "dup", Enums.EventStatus.DRAFT, 5, "Jaipur", "Music");
        Event clash = new Event();
        clash.setOrganizerId(o.getId()); clash.setName("x"); clash.setSlug(e.getSlug()); clash.setStartsAt(Instant.now().plusSeconds(86_400));
        assertThrows(Exception.class, () -> events.saveAndFlush(clash));
    }

    @Test void inventoryCannotBeReducedBelowCommittedQuantity() {
        Organizer o = organizer("inv2");
        Event e = event(o, "cap", Enums.EventStatus.PUBLISHED, 5, "Jaipur", "Music");
        TicketType t = type(e, "GA", 10_000, 10);
        reservations.reserve(t.getId(), 6);
        var shrink = new EventManagementService.UpdateTicketType(null, null, null, 5, null, null, null, null, null, null, null);
        ApiException ex = assertThrows(ApiException.class, () -> management.updateTicketType(t.getPublicId(), shrink, 1L, "ADMIN"));
        assertEquals("INVENTORY_BELOW_COMMITTED", ex.code());
    }
}
