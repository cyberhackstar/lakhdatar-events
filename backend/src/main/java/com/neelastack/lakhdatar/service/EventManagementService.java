package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EventManagementService {
    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final EventManagerAssignmentRepository managerAssignments;
    private final EventAccessService eventAccess;
    private final TicketRepository tickets;
    private final TicketReservationService reservations;
    private final VenueRepository venues;
    private final BrandConfigurationRepository brands;
    private final TicketTypeRepository ticketTypes;
    private final AuditService audit;
    private final AppProperties props;
    private final PaymentRepository payments;
    private final PaymentGatewayRouter paymentGateways;

    public record CreateTicketType(String name, String description, long priceMinorUnits, int totalQuantity,
                                   int minPerOrder, int maxPerOrder, Instant saleStartsAt, Instant saleEndsAt) {}
    public record CreateEventRequest(String organizerSlug, String slug, String name, String description, Instant startsAt,
                                     Instant endsAt, Integer capacity, String venueName, String venueAddress, String city,
                                     String organizerLogoUrl, String eventLogoUrl, String eventBannerUrl, String brandingMode,
                                     String primaryBrandColor, String secondaryBrandColor, String technologyPartnerUrl,
                                     List<CreateTicketType> ticketTypes,
                                     String paymentProvider,
                                     String shortDescription, String category, String timezone, String coverImageUrl,
                                     List<String> galleryUrls, List<String> highlights, Instant bookingStartsAt,
                                     Instant bookingEndsAt, String terms, String refundPolicy, String ageRestriction,
                                     String mapUrl, String state, Boolean featured, Integer displayOrder) {}
    public record UpdateEventRequest(String name, String shortDescription, String description, String category,
                                     Instant startsAt, Instant endsAt, String timezone, String venueName, String venueAddress, String city,
                                     String state, String mapUrl, String coverImageUrl, List<String> galleryUrls,
                                     List<String> highlights, Instant bookingStartsAt, Instant bookingEndsAt, String terms,
                                     String refundPolicy, String ageRestriction, Boolean featured, Integer displayOrder,
                                     Boolean clearEndsAt, Boolean clearBookingStartsAt, Boolean clearBookingEndsAt, String paymentProvider, String organizerLogoUrl, String eventLogoUrl, String eventBannerUrl, String brandingMode) {}
    public record UpdateTicketType(String name, String description, Long priceMinorUnits, Integer totalQuantity,
                                   Integer minPerOrder, Integer maxPerOrder, Instant saleStartsAt, Instant saleEndsAt, String status,
                                   Boolean clearSaleStartsAt, Boolean clearSaleEndsAt) {}
    public record TicketTypeCreated(UUID id, String name) {}
    public record AdminTicketView(UUID id, String name, String description, long priceMinorUnits, String currency,
                                  int totalQuantity, int soldQuantity, int reservedQuantity, int availableQuantity,
                                  int minPerOrder, int maxPerOrder, String status, Instant saleStartsAt, Instant saleEndsAt) {}
    public record PublishReadinessView(boolean ready, List<String> blockers, List<String> warnings) {}
    public record AdminEventView(UUID id, String slug, String name, String shortDescription, String description,
                                 String category, Instant startsAt, Instant endsAt, Integer capacity, String timezone,
                                 String currency, String venueName, String venueAddress, String city, String state,
                                 String mapUrl, String coverImageUrl, List<String> gallery, List<String> highlights,
                                 Instant bookingStartsAt, Instant bookingEndsAt, String terms, String refundPolicy,
                                 String ageRestriction, boolean featured, int displayOrder, String status,
                                 String organizerName, String organizerSlug, String paymentProvider, String brandingMode, String organizerLogoUrl, String eventLogoUrl, String eventBannerUrl, List<AdminTicketView> ticketTypes) {}

    /** Slugs that would collide with fixed catalogue routes (/events/featured etc). */
    private static final java.util.Set<String> RESERVED_SLUGS = java.util.Set.of("featured", "upcoming", "search", "facets", "new", "admin");
    public record CreatedEvent(UUID id, String slug, String name, String status) {}

    @Transactional
    public CreatedEvent create(CreateEventRequest r, Long actorId, String role) {
        if (!organizerManagementRole(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Role is not allowed to create events");
        if (r.name() == null || r.name().trim().length() < 3 || r.name().trim().length() > 180)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Event name must be between 3 and 180 characters");
        if (r.slug() == null || !r.slug().matches("[a-z0-9]+(?:-[a-z0-9]+){0,80}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "Use a lowercase URL-safe slug");
        if (r.startsAt() == null || r.startsAt().isBefore(Instant.now().minusSeconds(300)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_START", "Event start time is invalid");
        validateTimezone(r.timezone());
        if (r.endsAt() != null && !r.endsAt().isAfter(r.startsAt()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_END", "Event end must be after start");
        if (r.capacity() != null && r.capacity() <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CAPACITY", "Event capacity must be greater than zero");
        if (r.ticketTypes() != null && r.ticketTypes().size() > 50)
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_TICKET_TYPES", "An event can have at most 50 ticket types");
        validateAssetUrl(r.organizerLogoUrl(), "organizer logo");
        validateAssetUrl(r.eventLogoUrl(), "event logo");
        validateAssetUrl(r.eventBannerUrl(), "event banner");
        validateColor(r.primaryBrandColor(), "primary brand color");
        validateColor(r.secondaryBrandColor(), "secondary brand color");
        if (RESERVED_SLUGS.contains(r.slug())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "This slug is reserved");
        validateAssetUrl(r.coverImageUrl(), "cover image");
        if (r.galleryUrls() != null) { if (r.galleryUrls().size() > 12) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GALLERY", "At most 12 gallery images"); r.galleryUrls().forEach(u -> validateAssetUrl(u, "gallery image")); }
        validateAssetUrl(r.mapUrl(), "map");
        validateWindow(r.bookingStartsAt(), r.bookingEndsAt(), r.startsAt(), r.endsAt());
        if (events.findBySlug(r.slug()).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "SLUG_EXISTS", "An event with this slug already exists");

        Organizer o = resolveOrganizer(r.organizerSlug(), actorId, role);
        if (!eventAccess.canManage(actorId, role, o.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this organizer");

        Venue v = new Venue();
        v.setOrganizerId(o.getId()); v.setName(value(r.venueName(), "Venue")); v.setAddress(r.venueAddress()); v.setCity(r.city());
        v.setState(r.state()); v.setMapUrl(r.mapUrl());
        venues.save(v);

        BrandConfiguration b = new BrandConfiguration();
        b.setScope("EVENT"); b.setOrganizerId(o.getId()); b.setOrganizerName(o.getName());
        b.setOrganizerLogoUrl(value(r.organizerLogoUrl(), o.getLogoUrl()));
        b.setEventLogoUrl(r.eventLogoUrl()); b.setEventBannerUrl(r.eventBannerUrl());
        b.setBrandingMode(parseBrandingMode(r.brandingMode()));
        b.setPrimaryBrandColor(value(r.primaryBrandColor(), "#D9A441")); b.setSecondaryBrandColor(value(r.secondaryBrandColor(), "#7A1F3D"));
        b.setTechnologyPartnerEnabled(true); b.setTechnologyPartnerName(props.branding().neelastackName());
        b.setTechnologyPartnerLogoUrl(props.branding().neelastackLogoUrl());
        // The public Neelastack destination is platform-controlled; organizers never supply a branding URL.
        b.setTechnologyPartnerUrl(props.branding().neelastackPublicUrl());
        b.setPromoEnabled(props.branding().promoEnabled()); b.setPromoTitle(props.branding().promoTitle());
        b.setPromoDescription(props.branding().promoDescription());
        b.setPromoCtaText(props.branding().promoCta()); b.setPromoCtaUrl(b.getTechnologyPartnerUrl());
        brands.save(b);

        Event e = new Event();
        e.setSlug(r.slug()); e.setName(r.name().trim()); e.setDescription(r.description()); e.setStartsAt(r.startsAt()); e.setEndsAt(r.endsAt());
        e.setCapacity(r.capacity()); e.setOrganizerId(o.getId()); e.setVenueId(v.getId()); e.setBrandConfigId(b.getId());
        e.setStatus(Enums.EventStatus.DRAFT); e.setCurrency("INR");
        e.setPaymentProvider(parsePaymentProvider(r.paymentProvider()));
        paymentGateways.requireConfigured(e.getPaymentProvider());
        e.setShortDescription(r.shortDescription()); e.setCategory(value(r.category(), "General").trim());
        e.setTimezone(value(r.timezone(), "Asia/Kolkata")); e.setCoverImageUrl(r.coverImageUrl());
        e.setGalleryUrls(join(r.galleryUrls())); e.setHighlights(join(r.highlights()));
        e.setBookingStartsAt(r.bookingStartsAt());
        e.setBookingEndsAt(r.bookingEndsAt() != null ? r.bookingEndsAt() : r.endsAt());
        e.setTerms(r.terms()); e.setRefundPolicy(r.refundPolicy()); e.setAgeRestriction(r.ageRestriction());
        e.setFeatured(Boolean.TRUE.equals(r.featured())); e.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        events.save(e);
        if ("EVENT_MANAGER".equals(role)) {
            EventManagerAssignment assignment = new EventManagerAssignment();
            assignment.setEventId(e.getId());
            assignment.setUserId(actorId);
            managerAssignments.save(assignment);
        }

        long ticketCapacity = 0;
        if (r.ticketTypes() != null) for (CreateTicketType x : r.ticketTypes()) {
            if (x.name() == null || x.name().isBlank() || x.name().trim().length() > 120 || x.priceMinorUnits() <= 0 || x.totalQuantity() <= 0)
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE", "Ticket name, price and quantity are invalid");
            if (x.priceMinorUnits() > Long.MAX_VALUE / Math.max(1, props.checkout().maxTicketsPerOrder()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_PRICE", "Ticket price is too large");
            if (x.totalQuantity() > 1_000_000)
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_QUANTITY", "Ticket quantity is too large");
            int min = x.minPerOrder();
            int max = x.maxPerOrder();
            if (min <= 0 || max < min || max > props.checkout().maxTicketsPerOrder())
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_BOUNDS", "Ticket purchase limits are invalid");
            if (x.saleStartsAt() != null && x.saleEndsAt() != null && !x.saleEndsAt().isAfter(x.saleStartsAt()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE_DATES", "Ticket sale end must be after sale start");
            Instant eventEnd = e.getEndsAt() != null ? e.getEndsAt() : e.getStartsAt();
            if (x.saleEndsAt() != null && eventEnd != null && x.saleEndsAt().isAfter(eventEnd))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE_DATES", "Ticket sale end cannot be after the event ends");
            TicketType t = new TicketType(); t.setEventId(e.getId()); t.setName(x.name().trim()); t.setDescription(x.description());
            t.setPriceMinorUnits(x.priceMinorUnits()); t.setCurrency("INR"); t.setTotalQuantity(x.totalQuantity());
            t.setMinPerOrder(min); t.setMaxPerOrder(max);
            t.setSaleStartsAt(x.saleStartsAt()); t.setSaleEndsAt(x.saleEndsAt()); t.setStatus(Enums.TicketTypeStatus.ACTIVE);
            ticketTypes.save(t); ticketCapacity = Math.addExact(ticketCapacity, x.totalQuantity());
        }
        if (e.getCapacity() != null && ticketCapacity > e.getCapacity())
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPACITY_EXCEEDED", "Ticket inventory exceeds event capacity");

        audit.log(actorId, "EVENT_CREATED", "EVENT", e.getPublicId().toString(), null);
        return new CreatedEvent(e.getPublicId(), e.getSlug(), e.getName(), e.getStatus().name());
    }

    @Transactional(readOnly = true)
    public AdminEventView getForAdmin(UUID eventPublicId, Long actorId, String role) {
        Event e = managed(eventPublicId, actorId, role);
        Venue v = e.getVenueId() == null ? null : venues.findById(e.getVenueId()).orElse(null);
        Organizer o = organizers.findById(e.getOrganizerId()).orElse(null);
        List<String> gallery = splitLines(e.getGalleryUrls());
        List<String> highlights = splitLines(e.getHighlights());
        List<AdminTicketView> tv = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId()).stream()
                .map(t -> new AdminTicketView(t.getPublicId(), t.getName(), t.getDescription(), t.getPriceMinorUnits(),
                        t.getCurrency(), t.getTotalQuantity(), t.getSoldQuantity(), t.getReservedQuantity(),
                        Math.max(0, t.availableQuantity()), t.getMinPerOrder(), t.getMaxPerOrder(),
                        t.getStatus().name(), t.getSaleStartsAt(), t.getSaleEndsAt())).toList();
        return new AdminEventView(e.getPublicId(), e.getSlug(), e.getName(), e.getShortDescription(), e.getDescription(),
                e.getCategory(), e.getStartsAt(), e.getEndsAt(), e.getCapacity(), e.getTimezone(), e.getCurrency(),
                v == null ? null : v.getName(), v == null ? null : v.getAddress(), v == null ? null : v.getCity(),
                v == null ? null : v.getState(), v == null ? null : v.getMapUrl(), e.getCoverImageUrl(), gallery, highlights,
                e.getBookingStartsAt(), e.getBookingEndsAt(), e.getTerms(), e.getRefundPolicy(), e.getAgeRestriction(),
                e.isFeatured(), e.getDisplayOrder(), e.getStatus().name(), o == null ? "Event organizer" : o.getName(),
                o == null ? null : o.getSlug(), e.getPaymentProvider().name(),
                bFor(e).getBrandingMode(), bFor(e).getOrganizerLogoUrl(), bFor(e).getEventLogoUrl(), bFor(e).getEventBannerUrl(), tv);
    }

    @Transactional(readOnly = true)
    public PublishReadiness.Result publishReadiness(UUID eventPublicId, Long actorId, String role) {
        Event e = managed(eventPublicId, actorId, role);
        List<TicketType> typeList = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId());
        boolean hasTickets = !typeList.isEmpty();
        boolean hasActive = typeList.stream().anyMatch(t -> t.getStatus() == Enums.TicketTypeStatus.ACTIVE);
        boolean providerConfigured;
        try {
            providerConfigured = paymentGateways.forProvider(e.getPaymentProvider()).isConfigured();
        } catch (RuntimeException ex) {
            providerConfigured = false;
        }
        return PublishReadiness.evaluate(role, e.getStatus(), e.getStartsAt(), e.getEndsAt(),
                e.getBookingStartsAt(), e.getBookingEndsAt(), hasTickets, hasActive, providerConfigured);
    }

    @Transactional
    public void publish(UUID eventPublicId, Long actorId, String role) {
        if (!organizerManagementRole(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can publish events");
        Event managedEvent = events.findByPublicIdForUpdate(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        authorize(managedEvent, actorId, role);
        Event e = managedEvent;
        // Publishing is idempotent so duplicate clicks/retries cannot create noisy 409s.
        if (e.getStatus() == Enums.EventStatus.PUBLISHED) return;
        List<TicketType> typeList = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId());
        boolean hasTickets = !typeList.isEmpty();
        boolean hasActive = typeList.stream().anyMatch(t -> t.getStatus() == Enums.TicketTypeStatus.ACTIVE);
        boolean providerConfigured;
        try { providerConfigured = paymentGateways.forProvider(e.getPaymentProvider()).isConfigured(); }
        catch (RuntimeException ex) { providerConfigured = false; }
        PublishReadiness.Result readiness = PublishReadiness.evaluate(role, e.getStatus(), e.getStartsAt(), e.getEndsAt(),
                e.getBookingStartsAt(), e.getBookingEndsAt(), hasTickets, hasActive, providerConfigured);
        if (!readiness.ready()) {
            String detail = readiness.blockers().isEmpty() ? "Event is not ready to publish" : readiness.blockers().get(0);
            throw new ApiException(HttpStatus.CONFLICT, "PUBLISH_NOT_READY", detail);
        }
        Instant now = Instant.now();
        if (e.getBookingEndsAt() == null && e.getEndsAt() != null) e.setBookingEndsAt(e.getEndsAt());
        validateWindow(e.getBookingStartsAt(), e.getBookingEndsAt(), e.getStartsAt(), e.getEndsAt());
        e.setStatus(Enums.EventStatus.PUBLISHED);
        if (e.getPublishedAt() == null) e.setPublishedAt(now);
        audit.log(actorId, "EVENT_PUBLISHED", "EVENT", e.getPublicId().toString(), null);
    }

    public enum Transition { UNPUBLISH, CANCEL, COMPLETE, ARCHIVE }

    /** Lifecycle change. Cancelling closes sales immediately and queues paid-order refunds through the asynchronous recovery pipeline. */
    @Transactional
    public void transition(UUID eventPublicId, Transition t, Long actorId, String role) {
        Event managedEvent = managed(eventPublicId, actorId, role);
        Event e = events.findByIdForUpdate(managedEvent.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        Enums.EventStatus from = e.getStatus();
        // Lifecycle endpoints are retry-safe: repeating a completed state is a no-op.
        if ((t == Transition.UNPUBLISH && from == Enums.EventStatus.UNPUBLISHED)
                || (t == Transition.CANCEL && from == Enums.EventStatus.CANCELLED)
                || (t == Transition.COMPLETE && from == Enums.EventStatus.COMPLETED)
                || (t == Transition.ARCHIVE && from == Enums.EventStatus.ARCHIVED)) return;
        Enums.EventStatus to;
        switch (t) {
            case UNPUBLISH -> { require(from == Enums.EventStatus.PUBLISHED, "Only a published event can be unpublished"); to = Enums.EventStatus.UNPUBLISHED; }
            case CANCEL -> {
                require(from == Enums.EventStatus.DRAFT || from == Enums.EventStatus.PUBLISHED || from == Enums.EventStatus.UNPUBLISHED, "This event can no longer be cancelled");
                to = Enums.EventStatus.CANCELLED;
                // Customer-facing ticket state must immediately reflect event cancellation.
                // The event row is already locked, so this bulk update cannot race checkout/inventory
                // mutations and avoids loading tens of thousands of tickets into Hibernate.
                int cancelledTickets = tickets.cancelIssuedForEvent(e.getId());
                int releasedReservations = reservations.releaseHeldForEvent(e.getId());
                EnterpriseLog.info(log, "event.cancellation.tickets_closed",
                        "event.category", "event", "event.id", e.getPublicId(),
                        "tickets.cancelled", cancelledTickets,
                        "reservations.released", releasedReservations);
            }
            case COMPLETE -> {
                Instant now = Instant.now();
                Instant eventEnd = e.getEndsAt() != null ? e.getEndsAt() : e.getStartsAt();
                require(from == Enums.EventStatus.PUBLISHED && eventEnd != null && !eventEnd.isAfter(now),
                        "Only a published event that has ended can be completed");
                to = Enums.EventStatus.COMPLETED;
            }
            case ARCHIVE -> { require(from != Enums.EventStatus.PUBLISHED && from != Enums.EventStatus.ARCHIVED, "Unpublish or cancel the event before archiving"); to = Enums.EventStatus.ARCHIVED; }
            default -> throw new IllegalStateException();
        }
        e.setStatus(to);
        audit.log(actorId, "EVENT_" + t.name(), "EVENT", e.getPublicId().toString(), from + "->" + to);
    }

    @Transactional
    public void update(UUID eventPublicId, UpdateEventRequest r, Long actorId, String role) {
        Event managedEvent = managed(eventPublicId, actorId, role);
        Event e = events.findByIdForUpdate(managedEvent.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        authorize(e, actorId, role);
        require(e.getStatus() != Enums.EventStatus.COMPLETED && e.getStatus() != Enums.EventStatus.ARCHIVED && e.getStatus() != Enums.EventStatus.CANCELLED, "This event can no longer be edited");
        Instant previousEventEnd = e.getEndsAt() != null ? e.getEndsAt() : e.getStartsAt();
        boolean bookingEndExplicit = Boolean.TRUE.equals(r.clearBookingEndsAt()) || r.bookingEndsAt() != null;
        if (r.name() != null) { String n = r.name().trim(); if (n.length() < 3 || n.length() > 180) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", "Event name must be between 3 and 180 characters"); e.setName(n); }
        if (r.startsAt() != null) {
            // Allow edits to an event already in progress when its existing start instant is unchanged.
            // A newly selected start must still be in the future.
            if (!Objects.equals(r.startsAt(), e.getStartsAt()) && r.startsAt().isBefore(Instant.now().minusSeconds(300)))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_START", "A changed event start time cannot be in the past");
            e.setStartsAt(r.startsAt());
        }
        if (Boolean.TRUE.equals(r.clearEndsAt())) e.setEndsAt(null); else if (r.endsAt() != null) e.setEndsAt(r.endsAt());
        if (e.getEndsAt() != null && !e.getEndsAt().isAfter(e.getStartsAt())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_END", "Event end must be after start");
        if (r.timezone() != null) e.setTimezone(validateTimezone(r.timezone()));
        if (Boolean.TRUE.equals(r.clearBookingStartsAt())) e.setBookingStartsAt(null); else if (r.bookingStartsAt() != null) e.setBookingStartsAt(r.bookingStartsAt());
        if (Boolean.TRUE.equals(r.clearBookingEndsAt())) e.setBookingEndsAt(null); else if (r.bookingEndsAt() != null) e.setBookingEndsAt(r.bookingEndsAt());
        // If booking end previously followed the event end, move it with the event end.
        if (!bookingEndExplicit && Objects.equals(e.getBookingEndsAt(), previousEventEnd)) e.setBookingEndsAt(e.getEndsAt());
        // A newly introduced multi-day end defaults the booking end to that event end.
        if (!bookingEndExplicit && e.getBookingEndsAt() == null && e.getEndsAt() != null) e.setBookingEndsAt(e.getEndsAt());
        validateWindow(e.getBookingStartsAt(), e.getBookingEndsAt(), e.getStartsAt(), e.getEndsAt());
        if (r.shortDescription() != null) e.setShortDescription(r.shortDescription());
        if (r.description() != null) e.setDescription(r.description());
        if (r.category() != null && !r.category().isBlank()) e.setCategory(r.category().trim());
        if (r.coverImageUrl() != null) { validateAssetUrl(r.coverImageUrl(), "cover image"); e.setCoverImageUrl(r.coverImageUrl().isBlank() ? null : r.coverImageUrl()); }
        if (r.galleryUrls() != null) { if (r.galleryUrls().size() > 12) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GALLERY", "At most 12 gallery images"); r.galleryUrls().forEach(u -> validateAssetUrl(u, "gallery image")); e.setGalleryUrls(join(r.galleryUrls())); }
        if (r.highlights() != null) e.setHighlights(join(r.highlights()));
        if (r.terms() != null) e.setTerms(r.terms());
        if (r.refundPolicy() != null) e.setRefundPolicy(r.refundPolicy());
        if (r.ageRestriction() != null) e.setAgeRestriction(r.ageRestriction());
        if (r.featured() != null) e.setFeatured(r.featured());
        if (r.displayOrder() != null) e.setDisplayOrder(r.displayOrder());
        BrandConfiguration brand = e.getBrandConfigId() == null ? null : brands.findById(e.getBrandConfigId()).orElse(null);
        if (brand != null) {
            if (r.organizerLogoUrl() != null) { validateAssetUrl(r.organizerLogoUrl(), "organizer logo"); brand.setOrganizerLogoUrl(r.organizerLogoUrl().isBlank() ? null : r.organizerLogoUrl()); }
            if (r.eventLogoUrl() != null) { validateAssetUrl(r.eventLogoUrl(), "event logo"); brand.setEventLogoUrl(r.eventLogoUrl().isBlank() ? null : r.eventLogoUrl()); }
            if (r.eventBannerUrl() != null) { validateAssetUrl(r.eventBannerUrl(), "event banner"); brand.setEventBannerUrl(r.eventBannerUrl().isBlank() ? null : r.eventBannerUrl()); }
            if (r.brandingMode() != null) brand.setBrandingMode(parseBrandingMode(r.brandingMode()));
            brands.save(brand);
        }
        if (r.paymentProvider() != null) { Enums.PaymentProvider next=parsePaymentProvider(r.paymentProvider()); paymentGateways.requireConfigured(next); if (next != e.getPaymentProvider()) { require(e.getStatus()==Enums.EventStatus.DRAFT || paymentsForEvent(e.getId())==0, "Payment provider cannot be changed after payment activity exists"); e.setPaymentProvider(next); } }
        if (e.getVenueId() != null && (r.venueName() != null || r.venueAddress() != null || r.city() != null || r.state() != null || r.mapUrl() != null)) {
            Venue v = venues.findById(e.getVenueId()).orElseThrow();
            if (!v.getOrganizerId().equals(e.getOrganizerId())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Venue belongs to another organizer");
            if (r.venueName() != null && !r.venueName().isBlank()) v.setName(r.venueName().trim());
            if (r.venueAddress() != null) v.setAddress(r.venueAddress());
            if (r.city() != null) v.setCity(r.city());
            if (r.state() != null) v.setState(r.state());
            if (r.mapUrl() != null) { validateAssetUrl(r.mapUrl(), "map"); v.setMapUrl(r.mapUrl().isBlank() ? null : r.mapUrl()); }
            venues.save(v);
        }
        audit.log(actorId, "EVENT_UPDATED", "EVENT", e.getPublicId().toString(), null);
    }

    @Transactional
    public TicketTypeCreated addTicketType(UUID eventPublicId, CreateTicketType x, Long actorId, String role) {
        Event managedEvent = managed(eventPublicId, actorId, role);
        // Event-wide capacity is an aggregate invariant across ticket types. Serialize every
        // inventory-configuration mutation on the same event row, matching the checkout lock order.
        Event e = events.findByIdForUpdate(managedEvent.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        require(e.getStatus() == Enums.EventStatus.DRAFT || e.getStatus() == Enums.EventStatus.PUBLISHED || e.getStatus() == Enums.EventStatus.UNPUBLISHED, "Ticket types can no longer be added");
        validateTicketType(x.name(), x.priceMinorUnits(), x.totalQuantity(), x.minPerOrder(), x.maxPerOrder(), x.saleStartsAt(), x.saleEndsAt(), e);
        TicketType t = new TicketType(); t.setEventId(e.getId()); t.setName(x.name().trim()); t.setDescription(x.description());
        t.setPriceMinorUnits(x.priceMinorUnits()); t.setCurrency(e.getCurrency()); t.setTotalQuantity(x.totalQuantity());
        t.setMinPerOrder(x.minPerOrder()); t.setMaxPerOrder(x.maxPerOrder()); t.setSaleStartsAt(x.saleStartsAt()); t.setSaleEndsAt(x.saleEndsAt());
        t.setStatus(Enums.TicketTypeStatus.ACTIVE);
        checkCapacity(e, 0, x.totalQuantity());
        ticketTypes.save(t);
        audit.log(actorId, "TICKET_TYPE_ADDED", "EVENT", e.getPublicId().toString(), t.getPublicId().toString());
        return new TicketTypeCreated(t.getPublicId(), t.getName());
    }

    /** Row-locked so concurrent checkouts and admin edits cannot corrupt inventory counters. */
    @Transactional
    public void updateTicketType(UUID ticketTypePublicId, UpdateTicketType r, Long actorId, String role) {
        TicketType found = ticketTypes.findByPublicId(ticketTypePublicId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        // Lock the parent event before the ticket type so capacity edits serialize with both
        // checkout inventory reservations and other admin inventory edits.
        Event e = events.findByIdForUpdate(found.getEventId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        authorize(e, actorId, role);
        TicketType t = ticketTypes.findByIdForUpdate(found.getId()).orElseThrow();
        String name = r.name() != null ? r.name() : t.getName();
        long price = r.priceMinorUnits() != null ? r.priceMinorUnits() : t.getPriceMinorUnits();
        int total = r.totalQuantity() != null ? r.totalQuantity() : t.getTotalQuantity();
        int min = r.minPerOrder() != null ? r.minPerOrder() : t.getMinPerOrder();
        int max = r.maxPerOrder() != null ? r.maxPerOrder() : t.getMaxPerOrder();
        Instant ss = Boolean.TRUE.equals(r.clearSaleStartsAt()) ? null : (r.saleStartsAt() != null ? r.saleStartsAt() : t.getSaleStartsAt());
        Instant se = Boolean.TRUE.equals(r.clearSaleEndsAt()) ? null : (r.saleEndsAt() != null ? r.saleEndsAt() : t.getSaleEndsAt());
        validateTicketType(name, price, total, min, max, ss, se, e);
        if (total < t.getReservedQuantity() + t.getSoldQuantity())
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_BELOW_COMMITTED", "Quantity cannot be lower than tickets already reserved or sold");
        checkCapacity(e, t.getTotalQuantity(), total);
        t.setName(name.trim()); if (r.description() != null) t.setDescription(r.description());
        t.setPriceMinorUnits(price); t.setTotalQuantity(total); t.setMinPerOrder(min); t.setMaxPerOrder(max);
        t.setSaleStartsAt(ss); t.setSaleEndsAt(se);
        if (r.status() != null) {
            try { t.setStatus(Enums.TicketTypeStatus.valueOf(r.status())); }
            catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown ticket type status"); }
        }
        ticketTypes.save(t);
        audit.log(actorId, "TICKET_TYPE_UPDATED", "EVENT", e.getPublicId().toString(), t.getPublicId().toString());
    }

    // ---- shared helpers for the lifecycle operations above
    private Event managed(UUID eventPublicId, Long actorId, String role) {
        if (!organizerManagementRole(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can manage event configuration");
        }
        return eventAccess.requireManagedEvent(eventPublicId, actorId, role);
    }
    private void authorize(Event e, Long actorId, String role) {
        if (!organizerManagementRole(role) || !eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
    }
    private long paymentsForEvent(Long eventId) { return payments.sumSuccessfulByEventId(eventId, java.util.List.of(Enums.PaymentStatus.PENDING, Enums.PaymentStatus.PAYMENT_INITIATED, Enums.PaymentStatus.AUTHORIZED, Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.COMPLETED, Enums.PaymentStatus.REFUND_PENDING, Enums.PaymentStatus.REFUNDED)); }
    private BrandConfiguration bFor(Event e) { return e.getBrandConfigId() == null ? new BrandConfiguration() : brands.findById(e.getBrandConfigId()).orElse(new BrandConfiguration()); }
    private String parseBrandingMode(String raw) { if (raw == null || raw.isBlank()) return "BOTH"; String v=raw.trim().toUpperCase(Locale.ROOT); if (!java.util.Set.of("TEXT_ONLY","LOGO_ONLY","BOTH").contains(v)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BRANDING_MODE","Unsupported branding display mode"); return v; }
    private Enums.PaymentProvider parsePaymentProvider(String raw) { if (raw == null || raw.isBlank()) return paymentGateways.defaultProvider(); try { return Enums.PaymentProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAYMENT_PROVIDER","Unsupported payment provider"); } }
    private void require(boolean ok, String message) { if (!ok) throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", message); }
    private void validateWindow(Instant from, Instant to, Instant eventStart, Instant eventEnd) {
        Instant effectiveEventEnd = eventEnd != null ? eventEnd : eventStart;
        if (from != null && eventStart != null && !from.isBefore(eventStart))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_WINDOW", "Booking start must be before the event begins");
        if (from != null && to != null && !to.isAfter(from))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_WINDOW", "Booking end must be after booking start");
        if (to != null && effectiveEventEnd != null && to.isAfter(effectiveEventEnd))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_WINDOW", "Booking can remain open only until the event ends");
    }
    private void validateTicketType(String name, long price, int total, int min, int max, Instant ss, Instant se, Event event) {
        if (name == null || name.isBlank() || name.trim().length() > 120 || price <= 0 || total <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE", "Ticket name, price and quantity are invalid");
        if (price > Long.MAX_VALUE / Math.max(1, props.checkout().maxTicketsPerOrder())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_PRICE", "Ticket price is too large");
        if (total > 1_000_000) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_QUANTITY", "Ticket quantity is too large");
        if (min <= 0 || max < min || max > props.checkout().maxTicketsPerOrder()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_BOUNDS", "Ticket purchase limits are invalid");
        if (ss != null && se != null && !se.isAfter(ss)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE_DATES", "Ticket sale end must be after sale start");
        if (se != null) {
            Instant eventEnd = event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt();
            if (eventEnd != null && se.isAfter(eventEnd))
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE_DATES", "Ticket sale cannot remain open past the event end");
        }
    }
    private void checkCapacity(Event e, int oldTotal, int newTotal) {
        if (e.getCapacity() == null) return;
        long sum = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId()).stream().mapToLong(TicketType::getTotalQuantity).sum() - oldTotal + newTotal;
        if (sum > e.getCapacity()) throw new ApiException(HttpStatus.BAD_REQUEST, "CAPACITY_EXCEEDED", "Ticket inventory exceeds event capacity");
    }
    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("\\R"))
                .map(String::trim).filter(x -> !x.isBlank()).toList();
    }
    private static String join(List<String> lines) {
        if (lines == null) return null;
        return lines.stream().filter(x -> x != null && !x.isBlank()).map(x -> x.trim().replaceAll("\\R", " ")).collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * The organizer is never guessed. An explicit slug is required unless exactly one organizer is in scope
     * (a member of one organizer, or a platform with a single organizer). Members can only target their own organizer.
     */
    private Organizer resolveOrganizer(String slug, Long actorId, String role) {
        if (slug != null && !slug.isBlank())
            return organizers.findBySlug(slug.trim().toLowerCase()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZER_NOT_FOUND", "Organizer not found"));
        List<Organizer> mine = organizers.findAll().stream()
                .filter(x -> eventAccess.canManage(actorId, role, x.getId()))
                .toList();
        if (mine.size() == 1) return mine.get(0);
        throw new ApiException(HttpStatus.BAD_REQUEST, "ORGANIZER_REQUIRED", "Specify which organizer this event belongs to");
    }

    private boolean organizerManagementRole(String role) {
        return "ADMIN".equals(role) || "ORGANIZER".equals(role);
    }
    private String validateTimezone(String timezone) {
        String value = value(timezone, "Asia/Kolkata").trim();
        try { java.time.ZoneId.of(value); return value; }
        catch (java.time.DateTimeException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE", "Invalid event timezone"); }
    }

    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private void validateAssetUrl(String value, String label) {
        if (value == null || value.isBlank()) return;
        if (value.length() > 500 || !(value.startsWith("/") || value.matches("https://[^\s]+")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BRANDING_URL", "Invalid " + label + " URL");
        if (value.startsWith("//") || value.contains("\"") || value.contains("'"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BRANDING_URL", "Invalid " + label + " URL");
    }
    private void validateColor(String value, String label) {
        if (value == null || value.isBlank()) return;
        if (!value.matches("^#[0-9A-Fa-f]{6}$"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BRANDING_COLOR", "Invalid " + label);
    }
}
