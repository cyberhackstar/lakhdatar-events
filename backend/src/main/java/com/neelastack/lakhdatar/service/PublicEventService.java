package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.domain.TicketType;
import com.neelastack.lakhdatar.domain.Venue;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.EventCatalogRepository;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import com.neelastack.lakhdatar.repository.TicketTypeRepository;
import com.neelastack.lakhdatar.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Public, read-only catalogue. Availability and prices shown here are informational;
 * checkout re-validates everything against the database under lock.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventService {

    /** Statuses whose detail page may be viewed publicly. Drafts, unpublished and archived events are hidden. */
    private static final Set<Enums.EventStatus> DETAIL_VISIBLE =
            Set.of(Enums.EventStatus.PUBLISHED, Enums.EventStatus.CANCELLED, Enums.EventStatus.COMPLETED);

    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final VenueRepository venues;
    private final TicketTypeRepository ticketTypes;
    private final EventCatalogRepository catalog;
    private final BrandService brand;

    // ------------------------------------------------------------------ DTOs

    public record TicketTypeView(UUID id, String name, String description, long priceMinorUnits, String currency,
                                 int availableQuantity, int minPerOrder, int maxPerOrder, String status,
                                 Instant saleStartsAt, Instant saleEndsAt) {}

    public record OrganizerView(String slug, String name, String logoUrl, String description, String website,
                                String contactEmail, String contactPhone, String address, String supportHours,
                                String instagramUrl, String facebookUrl) {}

    public record EventView(UUID id, String slug, String name, String description, Instant startsAt, Instant endsAt,
                            Integer capacity, String currency, String venueName, String venueAddress,
                            BrandService.BrandView brand, List<TicketTypeView> ticketTypes,
                            String shortDescription, String category, String timezone, String coverImageUrl,
                            List<String> gallery, List<String> highlights, Instant bookingStartsAt, Instant bookingEndsAt,
                            String terms, String refundPolicy, String ageRestriction, String city, String state,
                            String country, String mapUrl, boolean featured, String status, String salesState,
                            Long startingPriceMinor, OrganizerView organizer, String paymentProvider) {}

    public record EventCard(UUID id, String slug, String name, String shortDescription, String category,
                            String coverImageUrl, Instant startsAt, Instant endsAt, String timezone, String venueName,
                            String city, String state, String organizerName, String organizerSlug, boolean featured,
                            String status, String salesState, Long startingPriceMinor, String currency,
                            int availableQuantity) {}

    public record PageView<T>(List<T> items, int page, int size, long total, int totalPages) {}

    public record Facets(List<String> categories, List<String> cities) {}

    public record SitemapEntry(String slug, Instant lastModified) {}

    public record Filter(String q, String category, String city, String organizer, Boolean featured,
                         Instant from, Instant to, Long minPriceMinor, Long maxPriceMinor) {}

    // ------------------------------------------------------------------ listing

    public PageView<EventCard> list(Filter f, int page, int size) {
        return toCards(catalog.search(new EventCatalogRepository.Criteria(f.q(), f.category(), f.city(), f.organizer(),
                f.featured(), f.from(), f.to(), f.minPriceMinor(), f.maxPriceMinor(), false, false), page, size), page, size);
    }

    public PageView<EventCard> featured(int limit) {
        int size = Math.max(1, Math.min(limit, 12));
        return toCards(catalog.search(new EventCatalogRepository.Criteria(null, null, null, null, true,
                null, null, null, null, false, true), 0, size), 0, size);
    }

    public PageView<EventCard> upcoming(int page, int size) {
        return toCards(catalog.search(new EventCatalogRepository.Criteria(null, null, null, null, null,
                null, null, null, null, false, false), page, size), page, size);
    }

    public Facets facets() {
        return new Facets(catalog.distinctCategories(), catalog.distinctCities());
    }

    /** Legacy full-list API retained for compatibility with existing callers/tests.
     * New SEO endpoints use the bounded paginated method below. */
    public List<SitemapEntry> sitemapEntries() {
        List<SitemapEntry> out = new ArrayList<>();
        for (Event e : events.findByStatusOrderByStartsAtAsc(Enums.EventStatus.PUBLISHED))
            out.add(new SitemapEntry(e.getSlug(), e.getUpdatedAt()));
        return out;
    }

    public long publishedEventCount() {
        return events.countByStatus(Enums.EventStatus.PUBLISHED);
    }

    public List<SitemapEntry> sitemapPage(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 10_000));
        Slice<Event> result = events.findByStatusOrderByStartsAtAsc(Enums.EventStatus.PUBLISHED,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "startsAt", "id")));
        return result.getContent().stream()
                .map(e -> new SitemapEntry(e.getSlug(), e.getUpdatedAt()))
                .toList();
    }

    private PageView<EventCard> toCards(EventCatalogRepository.Page result, int page, int size) {
        List<Long> ids = result.rows().stream().map(r -> r.event().getId()).toList();
        Map<Long, long[]> agg = new HashMap<>(); // [minPriceOnSale, availableAllActive, totalAllActive, onSaleTypeCount, onSaleAvailable]
        if (!ids.isEmpty()) {
            for (Object[] r : ticketTypes.aggregateForEvents(ids, Enums.TicketTypeStatus.ACTIVE, Instant.now())) {
                long minPrice = r[1] == null ? -1L : ((Number) r[1]).longValue();
                agg.put(((Number) r[0]).longValue(), new long[]{
                        minPrice, Math.max(0, ((Number) r[2]).longValue()), ((Number) r[3]).longValue(),
                        ((Number) r[4]).longValue(), Math.max(0, ((Number) r[5]).longValue())});
            }
        }
        Instant now = Instant.now();
        List<EventCard> cards = new ArrayList<>();
        for (EventCatalogRepository.Row row : result.rows()) {
            Event e = row.event();
            long[] a = agg.get(e.getId());
            long available = a == null ? 0 : a[1];
            long total = a == null ? 0 : a[2];
            long onSaleTypes = a == null ? 0 : a[3];
            long onSaleAvailable = a == null ? 0 : a[4];
            cards.add(new EventCard(e.getPublicId(), e.getSlug(), e.getName(), e.getShortDescription(), e.getCategory(),
                    e.getCoverImageUrl(), e.getStartsAt(), e.getEndsAt(), e.getTimezone(),
                    row.venue() == null ? null : row.venue().getName(),
                    row.venue() == null ? null : row.venue().getCity(),
                    row.venue() == null ? null : row.venue().getState(),
                    row.organizer().getName(), row.organizer().getSlug(), e.isFeatured(), e.getStatus().name(),
                    salesState(e, onSaleTypes > 0, onSaleAvailable, total, now), (a == null || a[0] < 0) ? null : a[0], e.getCurrency(), (int) Math.min(available, Integer.MAX_VALUE)));
        }
        int totalPages = (int) Math.ceil(result.total() / (double) size);
        return new PageView<>(cards, page, size, result.total(), totalPages);
    }

    // ------------------------------------------------------------------ detail

    public EventView getBySlug(String slug) {
        Event e = events.findBySlug(slug == null ? "" : slug.toLowerCase())
                .filter(x -> DETAIL_VISIBLE.contains(x.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        Organizer o = organizers.findById(e.getOrganizerId()).orElse(null);
        Venue v = e.getVenueId() == null ? null : venues.findById(e.getVenueId()).orElse(null);
        List<TicketType> types = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId());
        List<TicketTypeView> t = types.stream().map(x -> new TicketTypeView(x.getPublicId(), x.getName(), x.getDescription(),
                x.getPriceMinorUnits(), x.getCurrency(), Math.max(0, x.availableQuantity()), x.getMinPerOrder(),
                x.getMaxPerOrder(), x.getStatus().name(), x.getSaleStartsAt(), x.getSaleEndsAt())).toList();

        long available = 0, total = 0, onSaleAvailable = 0; Long minPrice = null; boolean hasOnSale = false;
        Instant now = Instant.now();
        for (TicketType x : types) {
            if (x.getStatus() != Enums.TicketTypeStatus.ACTIVE) continue;
            long remaining = Math.max(0, x.availableQuantity());
            available += remaining; total += x.getTotalQuantity();
            boolean onSale = (x.getSaleStartsAt() == null || !x.getSaleStartsAt().isAfter(now)) && (x.getSaleEndsAt() == null || x.getSaleEndsAt().isAfter(now));
            if (onSale) {
                hasOnSale = true;
                onSaleAvailable += remaining;
                if (minPrice == null || x.getPriceMinorUnits() < minPrice) minPrice = x.getPriceMinorUnits();
            }
        }
        String organizerName = o == null ? "Event organizer" : o.getName();
        BrandService.BrandView b = brand.view(e.getBrandConfigId(), organizerName);
        String cover = e.getCoverImageUrl() != null ? e.getCoverImageUrl() : b.eventBannerUrl();
        String address = v == null ? null : ((v.getAddress() == null ? "" : v.getAddress() + ", ") + (v.getCity() == null ? "" : v.getCity()));
        OrganizerView ov = o == null ? null : new OrganizerView(o.getSlug(), o.getName(),
                o.getLogoUrl() != null ? o.getLogoUrl() : b.organizerLogoUrl(), o.getDescription(), o.getWebsite(),
                o.getContactEmail(), o.getContactPhone(), o.getAddress(), o.getSupportHours(), o.getInstagramUrl(), o.getFacebookUrl());
        return new EventView(e.getPublicId(), e.getSlug(), e.getName(), e.getDescription(), e.getStartsAt(), e.getEndsAt(),
                e.getCapacity(), e.getCurrency(), v == null ? null : v.getName(), address, b, t,
                e.getShortDescription(), e.getCategory(), e.getTimezone(), cover, urlLines(e.getGalleryUrls(), 12),
                textLines(e.getHighlights(), 12), e.getBookingStartsAt(), e.getBookingEndsAt(),
                e.getTerms() != null ? e.getTerms() : (o == null ? null : o.getTerms()), e.getRefundPolicy(), e.getAgeRestriction(),
                v == null ? null : v.getCity(), v == null ? null : v.getState(), v == null ? null : v.getCountry(),
                v == null ? null : v.getMapUrl(), e.isFeatured(), e.getStatus().name(),
                salesState(e, hasOnSale, onSaleAvailable, total, now), minPrice, ov, e.getPaymentProvider().name());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Customer-facing sales state. Derived, never stored, so it cannot drift from inventory.
     * AVAILABLE | SELLING_FAST | SOLD_OUT | BOOKING_NOT_STARTED | BOOKING_CLOSED | CANCELLED | COMPLETED
     */
    public static String salesState(Event e, boolean hasActiveTypes, long available, long total, Instant now) {
        if (e.getStatus() == Enums.EventStatus.CANCELLED) return "CANCELLED";
        if (e.getStatus() == Enums.EventStatus.COMPLETED) return "COMPLETED";
        if (!e.getStartsAt().isAfter(now)) {
            Instant end = e.getEndsAt() != null ? e.getEndsAt() : e.getStartsAt();
            return end.isAfter(now) ? "BOOKING_CLOSED" : "COMPLETED";
        }
        if (e.getBookingStartsAt() != null && e.getBookingStartsAt().isAfter(now)) return "BOOKING_NOT_STARTED";
        if (e.getBookingEndsAt() != null && !e.getBookingEndsAt().isAfter(now)) return "BOOKING_CLOSED";
        if (!hasActiveTypes || available <= 0) return "SOLD_OUT";
        if (total > 0 && available * 5 <= total) return "SELLING_FAST";
        return "AVAILABLE";
    }

    /** True when new checkouts may start for this event at {@code now}. Used by OrderService. */
    public static boolean bookingWindowOpen(Event e, Instant now) {
        if (e.getBookingStartsAt() != null && e.getBookingStartsAt().isAfter(now)) return false;
        return e.getBookingEndsAt() == null || e.getBookingEndsAt().isAfter(now);
    }

    private static List<String> textLines(String raw, int max) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) for (String line : raw.split("\\R")) {
            String s = line.trim();
            if (!s.isEmpty() && s.length() <= 300) out.add(s);
            if (out.size() >= max) break;
        }
        return new ArrayList<>(out);
    }

    private static List<String> urlLines(String raw, int max) {
        List<String> out = new ArrayList<>();
        for (String s : textLines(raw, max)) if (s.startsWith("/") && !s.startsWith("//") || s.startsWith("https://")) out.add(s);
        return out;
    }
}
