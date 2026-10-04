package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.Writer;
import java.io.StringWriter;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final OrganizerMemberRepository members;
    private final EventManagerAssignmentRepository managerAssignments;
    private final TicketTypeRepository ticketTypes;
    private final TicketRepository tickets;
    private final PaymentRepository payments;
    private final TicketCheckinRepository checkins;
    private final EventStaffRepository staff;
    private final UserRepository users;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final EventAccessService eventAccess;
    private final TeamService team;
    private final JdbcTemplate jdbc;

    public record EventSummary(UUID id, String slug, String name, String status,
                               java.time.Instant startsAt, long ticketsSold, long ticketsCheckedIn,
                               long revenueMinor) {}

    public record Dashboard(List<EventSummary> events, long totalSold, long totalCheckedIn,
                            long totalRevenueMinor, long totalEvents, long publishedEvents, long draftEvents) {}
    private record EventScope(String clause, List<Object> args) {}
    public record EventCursorPage(List<EventSummary> items, String nextCursor, boolean hasNext, int size, long total) {}
    private record EventCursor(long epochMillis, long id) {}

    public record ManagerView(UUID userId, String email, String fullName, String role, String eventName) {}
    public record ManagerTicketType(UUID id, String name, long priceMinorUnits, int availableQuantity, String status) {}

    public Dashboard dashboard(UserPrincipal p) {
        // Keep the dashboard bounded: the portfolio list is recent-only, while totals are computed
        // as database aggregates over the actor's authorized event scope. This avoids loading every
        // event/ticket/order row into JVM heap when the platform grows to thousands of events.
        EventCursorPage recent = eventCursor(p, "", "", null, 6);
        EventScope scope = eventScope(p);
        String scoped = " where 1=1" + scope.clause();
        String sql = "with scoped_events as (select e.id,e.status from events e" + scoped + ") "
                + "select "
                + "(select coalesce(sum(tt.sold_quantity),0)::bigint from ticket_types tt join scoped_events se on se.id=tt.event_id),"
                + "(select count(*)::bigint from tickets t join scoped_events se on se.id=t.event_id where t.status='CHECKED_IN'),"
                + "(select coalesce(sum(p.amount_minor),0)::bigint from payments p join orders o on o.id=p.order_id join scoped_events se on se.id=o.event_id where p.status in ('CAPTURED','COMPLETED')),"
                + "(select count(*)::bigint from scoped_events),"
                + "(select count(*)::bigint from scoped_events where status='PUBLISHED'),"
                + "(select count(*)::bigint from scoped_events where status='DRAFT')";
        List<Object> args = new ArrayList<>(scope.args());
        long[] totals = jdbc.queryForObject(sql, args.toArray(), (rs, n) -> new long[]{
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6)
        });
        return new Dashboard(recent.items(), totals[0], totals[1], totals[2], totals[3], totals[4], totals[5]);
    }

    private EventScope eventScope(UserPrincipal p) {
        if (p == null || p.userId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized");
        if ("ADMIN".equalsIgnoreCase(p.role())) return new EventScope("", List.of());
        if ("ORGANIZER".equalsIgnoreCase(p.role())) {
            return new EventScope(" and exists (select 1 from organizer_members om where om.organizer_id=e.organizer_id and om.user_id=? and om.role in ('OWNER','ORGANIZER'))", List.of(p.userId()));
        }
        if ("EVENT_MANAGER".equalsIgnoreCase(p.role())) {
            return new EventScope(" and exists (select 1 from event_manager_assignments ema where ema.event_id=e.id and ema.user_id=?)", List.of(p.userId()));
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized to view event dashboard");
    }

    private static List<Object> concat(List<Object>... lists){List<Object> out=new ArrayList<>(); for(List<Object> l:lists) out.addAll(l); return out;}

    /** @deprecated Use the cursor event API. This compatibility endpoint intentionally returns only the recent six events. */
    @Deprecated
    public List<EventSummary> events(UserPrincipal p) {
        return dashboard(p).events();
    }

    public EventCursorPage eventCursor(UserPrincipal p, String q, String status, String cursor, int size) {
        int safeSize = Math.min(100, Math.max(1, size));
        if (p == null || p.userId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized");
        List<Object> args = new ArrayList<>();
        String where = " where 1=1";
        if ("ADMIN".equals(p.role())) {
            // platform-wide
        } else if ("ORGANIZER".equals(p.role())) {
            where += " and exists (select 1 from organizer_members om where om.organizer_id=e.organizer_id and om.user_id=? and om.role in ('OWNER','ORGANIZER'))";
            args.add(p.userId());
        } else if ("EVENT_MANAGER".equals(p.role())) {
            where += " and exists (select 1 from event_manager_assignments ema where ema.event_id=e.id and ema.user_id=?)";
            args.add(p.userId());
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized to view events");
        }
        String cleanQ = q == null ? "" : q.trim();
        if (cleanQ.length() > 120) cleanQ = cleanQ.substring(0,120);
        if (!cleanQ.isBlank()) {
            where += " and (e.name ilike ? or e.slug ilike ? or e.category ilike ?)";
            String pattern = "%" + cleanQ + "%";
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        String cleanStatus = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!cleanStatus.isBlank()) {
            try { Enums.EventStatus.valueOf(cleanStatus); }
            catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown event status"); }
            where += " and e.status=?"; args.add(cleanStatus);
        }
        EventCursor c = decodeEventCursor(cursor);
        if (c != null) {
            where += " and (e.starts_at < to_timestamp(? / 1000.0) or (e.starts_at = to_timestamp(? / 1000.0) and e.id < ?))";
            args.add(c.epochMillis()); args.add(c.epochMillis()); args.add(c.id());
        }
        Long totalValue = jdbc.queryForObject("select count(*) from events e" + where, args.toArray(), Long.class);
        List<Object> queryArgs = new ArrayList<>(args); queryArgs.add(safeSize + 1);
        List<EventTimed> rows = jdbc.query(
                "select e.public_id,e.slug,e.name,e.status,e.starts_at," +
                "coalesce((select sum(tt.sold_quantity)::bigint from ticket_types tt where tt.event_id=e.id),0)," +
                "coalesce((select count(*)::bigint from tickets t where t.event_id=e.id and t.status='CHECKED_IN'),0)," +
                "coalesce((select sum(p.amount_minor)::bigint from payments p join orders o on o.id=p.order_id where o.event_id=e.id and p.status in ('CAPTURED','COMPLETED')),0),e.id " +
                "from events e" + where + " order by e.starts_at desc,e.id desc limit ?",
                queryArgs.toArray(), (rs,n) -> new EventTimed(
                        new EventSummary(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getLong(6),rs.getLong(7),rs.getLong(8)),
                        rs.getTimestamp(5).toInstant(),rs.getLong(9)));
        boolean more = rows.size() > safeSize;
        if (more) rows = new ArrayList<>(rows.subList(0, safeSize));
        String next = more ? encodeEventCursor(rows.get(rows.size()-1).time(), rows.get(rows.size()-1).id()) : null;
        return new EventCursorPage(rows.stream().map(EventTimed::value).toList(), next, more, safeSize, totalValue == null ? 0L : totalValue);
    }

    private record EventTimed(EventSummary value, java.time.Instant time, long id) {}
    private static String encodeEventCursor(java.time.Instant time,long id){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((time.toEpochMilli()+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static EventCursor decodeEventCursor(String raw){if(raw==null||raw.isBlank())return null;try{String s=new String(java.util.Base64.getUrlDecoder().decode(raw),java.nio.charset.StandardCharsets.UTF_8);String[] p=s.split(":",2);if(p.length!=2)throw new IllegalArgumentException();long t=Long.parseLong(p[0]),id=Long.parseLong(p[1]);if(t<=0||id<=0)throw new IllegalArgumentException();return new EventCursor(t,id);}catch(Exception ex){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","Cursor is invalid");}}

    @Transactional
    public void assignStaff(UUID eventPublicId, String email, String gate, Long actorId, String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can manage event staff");
        }
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        User u = users.findByEmailIgnoreCase(email.trim()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Staff user not found"));
        if (u.getRole() != Enums.UserRole.STAFF) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STAFF_ROLE", "Only STAFF users can be assigned to event gates");
        }
        team.requireTeamMemberOf(e.getOrganizerId(), u, "STAFF", role);
        EventStaff s = staff.findByEventIdAndUserId(e.getId(), u.getId()).orElseGet(EventStaff::new); // upsert = idempotent
        s.setEventId(e.getId());
        s.setUserId(u.getId());
        s.setGate(team.cleanGate(gate));
        staff.save(s);
        audit.log(actorId, "STAFF_ASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    /** @deprecated ADMIN-only legacy path. Use TeamService via /admin/organizers/{slug}/team/staff. */
    @Deprecated
    public void createStaff(String email, String name, String password, Long actorId, String role) {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an administrator can provision staff users");
        }
        createUser(email, name, password, Enums.UserRole.STAFF, actorId, "STAFF_CREATED");
    }

    /** @deprecated ADMIN-only legacy path. Use TeamService via /admin/organizers/{slug}/team/managers. */
    @Deprecated
    public void createManager(String email, String name, String password, Long actorId, String role) {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an administrator can provision event managers");
        }
        createUser(email, name, password, Enums.UserRole.EVENT_MANAGER, actorId, "EVENT_MANAGER_CREATED");
    }

    @Transactional
    public void assignManager(UUID eventPublicId, String email, Long actorId, String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can assign event managers");
        }
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        User u = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Event manager account not found"));
        if (u.getRole() != Enums.UserRole.EVENT_MANAGER) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_MANAGER_ROLE", "The selected account is not an EVENT_MANAGER");
        }
        team.requireTeamMemberOf(e.getOrganizerId(), u, "EVENT_MANAGER", role);
        if (!managerAssignments.existsByEventIdAndUserId(e.getId(), u.getId())) {
            EventManagerAssignment assignment = new EventManagerAssignment();
            assignment.setEventId(e.getId());
            assignment.setUserId(u.getId());
            managerAssignments.save(assignment);
        }
        audit.log(actorId, "EVENT_MANAGER_ASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    @Transactional
    public void unassignManager(UUID eventPublicId, String email, Long actorId, String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can remove event managers");
        }
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        User u = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Event manager account not found"));
        managerAssignments.deleteByEventIdAndUserId(e.getId(), u.getId());
        audit.log(actorId, "EVENT_MANAGER_UNASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    public List<ManagerTicketType> ticketTypesForEvent(UUID eventPublicId, UserPrincipal p) {
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        return ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId()).stream()
                .map(t -> new ManagerTicketType(t.getPublicId(), t.getName(), t.getPriceMinorUnits(), t.availableQuantity(), t.getStatus().name()))
                .toList();
    }

    public List<ManagerView> managersForEvent(UUID eventPublicId, UserPrincipal p) {
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        List<EventManagerAssignment> assignments=managerAssignments.findByEventIdOrderByUserIdAsc(e.getId());
        Map<Long,User> byId=users.findAllById(assignments.stream().map(EventManagerAssignment::getUserId).toList()).stream().collect(java.util.stream.Collectors.toMap(User::getId,java.util.function.Function.identity()));
        return assignments.stream().map(EventManagerAssignment::getUserId).map(byId::get).filter(Objects::nonNull)
                .map(u->new ManagerView(u.getPublicId(),u.getEmail(),u.getFullName(),u.getRole().name(),e.getName())).toList();
    }

    /**
     * Streams attendees directly from PostgreSQL so a large event does not require the complete
     * ticket set, issuer map, and CSV buffer to coexist in heap memory. The method stays transactional
     * for the duration of the stream and uses a forward-only JDBC cursor.
     */
    @Transactional(readOnly = true)
    public void writeAttendeesCsv(UUID eventPublicId, UserPrincipal p, Writer writer) throws IOException {
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role()) && !canViewOrganizer(e.getOrganizerId(), p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized");
        }
        writer.write("ticket_number,attendee_name,ticket_status,ticket_source,issued_by,created_at,checked_in_at\n");
        try {
            jdbc.query(con -> {
            var ps = con.prepareStatement(
                    "select t.ticket_number,t.attendee_name,t.status,t.source,u.email,t.created_at,t.checked_in_at " +
                    "from tickets t left join users u on u.id=t.issued_by_user_id " +
                    "where t.event_id=? order by t.ticket_number",
                    java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
            ps.setLong(1, e.getId());
            ps.setFetchSize(1000);
            return ps;
        }, rs -> {
            try {
                writer.write(csv(rs.getString(1))); writer.write(',');
                writer.write(csv(rs.getString(2))); writer.write(',');
                writer.write(String.valueOf(rs.getString(3))); writer.write(',');
                writer.write(String.valueOf(rs.getString(4))); writer.write(',');
                writer.write(csv(rs.getString(5))); writer.write(',');
                writer.write(String.valueOf(rs.getTimestamp(6).toInstant())); writer.write(',');
                var checkedIn = rs.getTimestamp(7);
                if (checkedIn != null) writer.write(checkedIn.toInstant().toString());
                writer.write('\n');
            } catch (IOException ex) {
                throw new CsvStreamingException(ex);
            }
            });
        } catch (CsvStreamingException ex) {
            throw ex.getIOException();
        }
        writer.flush();
    }

    /** Backward-compatible bounded helper for non-streaming callers. */
    public String attendeesCsv(UUID eventPublicId, UserPrincipal p) {
        StringWriter writer = new StringWriter();
        try { writeAttendeesCsv(eventPublicId, p, writer); return writer.toString(); }
        catch (IOException e) { throw new IllegalStateException("CSV export failed", e); }
    }

    private static final class CsvStreamingException extends RuntimeException {
        CsvStreamingException(IOException cause) { super(cause); }
        IOException getIOException() { return (IOException) getCause(); }
    }

    private void createUser(String email, String name, String password, Enums.UserRole targetRole,
                            Long actorId, String auditAction) {
        String normalized = email.trim().toLowerCase();
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "User already exists");
        }
        User u = new User();
        u.setEmail(normalized);
        u.setFullName(name.trim());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(targetRole);
        u.setEnabled(true);
        users.save(u);
        audit.log(actorId, auditAction, "USER", u.getPublicId().toString(), null);
    }

    private boolean canViewOrganizer(Long organizerId, UserPrincipal p) {
        if (p == null || p.userId() == null) return false;
        if ("ADMIN".equals(p.role())) return true;
        return members.findByOrganizerIdAndUserId(organizerId, p.userId()).map(m -> {
            if ("FINANCE".equals(p.role())) return "OWNER".equalsIgnoreCase(m.getRole()) || "FINANCE".equalsIgnoreCase(m.getRole());
            if ("SUPPORT".equals(p.role())) return "OWNER".equalsIgnoreCase(m.getRole()) || "SUPPORT".equalsIgnoreCase(m.getRole());
            return "OWNER".equalsIgnoreCase(m.getRole()) || "ORGANIZER".equalsIgnoreCase(m.getRole());
        }).orElse(false);
    }

    private String csv(String s) {
        String value = s == null ? "" : s;
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
