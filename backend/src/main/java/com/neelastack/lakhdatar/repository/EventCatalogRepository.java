package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.domain.Venue;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only, server-side filtered and paginated catalogue queries for the public site.
 * The query text is assembled from fixed fragments only; every user-supplied value is a bound parameter.
 */
@Repository
public class EventCatalogRepository {

    public record Criteria(String q, String category, String city, String organizerSlug, Boolean featured,
                           Instant from, Instant to, Long minPriceMinor, Long maxPriceMinor,
                           boolean includeEnded, boolean featuredFirst) {}

    public record Row(Event event, Venue venue, Organizer organizer) {}

    public record Page(List<Row> rows, long total) {}

    @PersistenceContext
    private EntityManager em;

    private static final String FROM =
            " from Event e left join Venue v on v.id = e.venueId join Organizer o on o.id = e.organizerId ";

    public Page search(Criteria c, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        String where = where(c, params);
        String order = c.featuredFirst()
                ? " order by e.featured desc, e.displayOrder asc, e.startsAt asc, e.id asc"
                : " order by e.startsAt asc, e.displayOrder asc, e.id asc";

        TypedQuery<Long> count = em.createQuery("select count(e)" + FROM + where, Long.class);
        params.forEach((k, v) -> count.setParameter(k, v));
        long total = count.getSingleResult();
        if (total == 0) return new Page(List.of(), 0);

        TypedQuery<Object[]> q = em.createQuery("select e, v, o" + FROM + where + order, Object[].class);
        params.forEach((k, v) -> q.setParameter(k, v));
        q.setFirstResult(Math.max(0, page) * size);
        q.setMaxResults(size);
        List<Row> rows = new ArrayList<>();
        for (Object[] r : q.getResultList()) rows.add(new Row((Event) r[0], (Venue) r[1], (Organizer) r[2]));
        return new Page(rows, total);
    }

    public List<String> distinctCategories() {
        return em.createQuery("select distinct e.category from Event e where e.status = :s and e.category is not null order by e.category",
                String.class).setParameter("s", Enums.EventStatus.PUBLISHED).getResultList();
    }

    public List<String> distinctCities() {
        return em.createQuery("select distinct v.city from Event e join Venue v on v.id = e.venueId where e.status = :s and v.city is not null order by v.city",
                String.class).setParameter("s", Enums.EventStatus.PUBLISHED).getResultList();
    }

    private String where(Criteria c, Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" where e.status = :status");
        p.put("status", Enums.EventStatus.PUBLISHED);
        if (!c.includeEnded()) {
            w.append(" and coalesce(e.endsAt, e.startsAt) >= :now");
            p.put("now", Instant.now());
        }
        if (notBlank(c.q())) {
            w.append(" and (lower(e.name) like :q or lower(coalesce(e.shortDescription,'')) like :q"
                    + " or lower(coalesce(v.name,'')) like :q or lower(coalesce(v.city,'')) like :q"
                    + " or lower(e.category) like :q)");
            p.put("q", "%" + escapeLike(c.q().trim().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        if (notBlank(c.category())) { w.append(" and lower(e.category) = :category"); p.put("category", c.category().trim().toLowerCase(java.util.Locale.ROOT)); }
        if (notBlank(c.city())) { w.append(" and lower(v.city) = :city"); p.put("city", c.city().trim().toLowerCase(java.util.Locale.ROOT)); }
        if (notBlank(c.organizerSlug())) { w.append(" and o.slug = :organizer"); p.put("organizer", c.organizerSlug().trim().toLowerCase(java.util.Locale.ROOT)); }
        if (c.featured() != null) { w.append(" and e.featured = :featured"); p.put("featured", c.featured()); }
        if (c.from() != null) { w.append(" and e.startsAt >= :from"); p.put("from", c.from()); }
        if (c.to() != null) { w.append(" and e.startsAt < :to"); p.put("to", c.to()); }
        if (c.minPriceMinor() != null || c.maxPriceMinor() != null) {
            w.append(" and exists (select 1 from TicketType t where t.eventId = e.id and t.status = :ttStatus");
            p.put("ttStatus", Enums.TicketTypeStatus.ACTIVE);
            if (c.minPriceMinor() != null) { w.append(" and t.priceMinorUnits >= :minPrice"); p.put("minPrice", c.minPriceMinor()); }
            if (c.maxPriceMinor() != null) { w.append(" and t.priceMinorUnits <= :maxPrice"); p.put("maxPrice", c.maxPriceMinor()); }
            w.append(")");
        }
        return w.toString();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
