package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTicketQueryService {
    private static final int MAX_SIZE = 100;
    private static final int MAX_PAGE = 100_000;

    private final JdbcTemplate jdbc;
    private final EventRepository events;
    private final EventAccessService access;

    public record PageView<T>(List<T> items, int page, int size, long total, int totalPages) {}
    public record OperationsPage<T>(UUID eventId, String eventName, List<T> items, int page, int size, long total, int totalPages) {}
    public record ScopedTicketRow(UUID eventId, String eventName, String eventSlug, UUID ticketId, String ticketNumber,
                                  String attendeeName, String email, String phone, String ticketType, long amountMinorUnits, String currency,
                                  String status, String source, String orderNumber, String issuedByName, Instant issuedAt, Instant checkedInAt) {}

    public record TicketRow(UUID ticketId, String ticketNumber, String attendeeName, String email, String phone,
                            String ticketType, long amountMinorUnits, String currency, String status, String source,
                            String orderNumber, String issuedByName, Instant issuedAt, Instant checkedInAt) {}

    public record OrderRow(UUID orderId, String orderNumber, String customerName, String customerEmail, String customerPhone,
                           long totalMinorUnits, String currency, String status, String paymentStatus, int ticketCount, Instant createdAt) {}

    /** Top-level ticket view. Scope is enforced in SQL: ADMIN sees all, ORGANIZER sees their owned organizations,
     * EVENT_MANAGER sees only explicitly assigned events. Optional eventPublicId further narrows the scope. */
    public PageView<ScopedTicketRow> scopedTickets(UserPrincipal actor, UUID eventPublicId, String q, String status, String source, int page, int size) {
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        if (page < 0 || page > MAX_PAGE) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page is out of range");
        if (actor == null || actor.userId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized");
        List<Object> args = new ArrayList<>();
        String where = " where 1=1";
        if ("ORGANIZER".equals(actor.role())) {
            where += " and exists (select 1 from organizer_members om where om.organizer_id=e.organizer_id and om.user_id=? and om.role in ('OWNER','ORGANIZER'))";
            args.add(actor.userId());
        } else if ("EVENT_MANAGER".equals(actor.role())) {
            where += " and exists (select 1 from event_manager_assignments ema where ema.event_id=e.id and ema.user_id=?)";
            args.add(actor.userId());
        } else if (!"ADMIN".equals(actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized to view issued tickets");
        }
        if (eventPublicId != null) { where += " and e.public_id=?"; args.add(eventPublicId); }
        String cleanQ = cleanQuery(q);
        if (!cleanQ.isBlank()) {
            where += " and (t.ticket_number ilike ? or coalesce(t.attendee_name,'') ilike ? or coalesce(o.customer_email,'') ilike ? or o.order_number ilike ? or e.name ilike ? or e.slug ilike ?)";
            String pattern = "%" + cleanQ + "%";
            args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
        }
        String cleanStatus = normalize(status);
        if (!cleanStatus.isBlank()) { validateEnum(cleanStatus, Enums.TicketStatus.values(), "status"); where += " and t.status=?"; args.add(cleanStatus); }
        String cleanSource = normalize(source);
        if (!cleanSource.isBlank()) { validateEnum(cleanSource, Enums.TicketSource.values(), "source"); where += " and t.source=?"; args.add(cleanSource); }

        long total = jdbc.queryForObject("select count(*) from tickets t join events e on e.id=t.event_id join orders o on o.id=t.order_id" + where, Long.class, args.toArray());
        int offset = page * safeSize;
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(safeSize); pageArgs.add(offset);
        List<ScopedTicketRow> rows = jdbc.query(
                "select e.public_id,e.name,e.slug,t.public_id,t.ticket_number,t.attendee_name,o.customer_email,o.customer_phone,tt.name,oi.unit_price_minor,o.currency,t.status,t.source,o.order_number,coalesce(u.full_name,''),t.created_at,t.checked_in_at " +
                "from tickets t join events e on e.id=t.event_id join orders o on o.id=t.order_id join order_items oi on oi.id=t.order_item_id join ticket_types tt on tt.id=t.ticket_type_id " +
                "left join users u on u.id=t.issued_by_user_id" + where + " order by t.created_at desc, t.id desc limit ? offset ?", (rs,rowNum) ->
                new ScopedTicketRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),
                        rs.getString(7),rs.getString(8),rs.getString(9),rs.getLong(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),
                        rs.getString(15),rs.getTimestamp(16).toInstant(),rs.getTimestamp(17)==null?null:rs.getTimestamp(17).toInstant()), pageArgs.toArray());
        return new PageView<>(rows, page, safeSize, total, total == 0 ? 0 : (int)Math.ceil(total/(double)safeSize));
    }

    public OperationsPage<TicketRow> tickets(UUID eventPublicId, UserPrincipal actor, String q, String status, String source, int page, int size) {
        Event e = requireEvent(eventPublicId, actor);
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        if (page < 0 || page > MAX_PAGE) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page is out of range");
        int safePage = page;
        int offset = safePage * safeSize;
        List<Object> args = new ArrayList<>();
        String where = " where t.event_id=?";
        args.add(e.getId());
        String cleanQ = cleanQuery(q);
        if (!cleanQ.isBlank()) {
            where += " and (t.ticket_number ilike ? or coalesce(t.attendee_name,'') ilike ? or coalesce(o.customer_email,'') ilike ? or o.order_number ilike ?)";
            String pattern = "%" + cleanQ + "%";
            args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
        }
        String cleanStatus = normalize(status);
        if (!cleanStatus.isBlank()) { validateEnum(cleanStatus, Enums.TicketStatus.values(), "status"); where += " and t.status=?"; args.add(cleanStatus); }
        String cleanSource = normalize(source);
        if (!cleanSource.isBlank()) { validateEnum(cleanSource, Enums.TicketSource.values(), "source"); where += " and t.source=?"; args.add(cleanSource); }

        long total = jdbc.queryForObject("select count(*) from tickets t join orders o on o.id=t.order_id" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(safeSize); pageArgs.add(offset);
        List<TicketRow> rows = jdbc.query("select t.public_id,t.ticket_number,t.attendee_name,o.customer_email,o.customer_phone,tt.name,oi.unit_price_minor,o.currency,t.status,t.source,o.order_number,coalesce(u.full_name,''),t.created_at,t.checked_in_at " +
                "from tickets t join orders o on o.id=t.order_id join order_items oi on oi.id=t.order_item_id join ticket_types tt on tt.id=t.ticket_type_id " +
                "left join users u on u.id=t.issued_by_user_id" + where + " order by t.created_at desc, t.id desc limit ? offset ?", (rs, rowNum) ->
                new TicketRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getLong(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getTimestamp(13).toInstant(), rs.getTimestamp(14) == null ? null : rs.getTimestamp(14).toInstant()), pageArgs.toArray());
        return new OperationsPage<>(e.getPublicId(), e.getName(), rows, safePage, safeSize, total, total == 0 ? 0 : (int)Math.ceil(total/(double)safeSize));
    }

    public OperationsPage<OrderRow> orders(UUID eventPublicId, UserPrincipal actor, String q, String status, int page, int size) {
        Event e = requireEvent(eventPublicId, actor);
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        if (page < 0 || page > MAX_PAGE) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page is out of range");
        int safePage = page;
        int offset = safePage * safeSize;
        List<Object> args = new ArrayList<>();
        String where = " where o.event_id=?"; args.add(e.getId());
        String cleanQ = cleanQuery(q);
        if (!cleanQ.isBlank()) {
            where += " and (o.order_number ilike ? or o.customer_name ilike ? or o.customer_email ilike ?)";
            String pattern = "%" + cleanQ + "%"; args.add(pattern); args.add(pattern); args.add(pattern);
        }
        String cleanStatus = normalize(status);
        if (!cleanStatus.isBlank()) { validateEnum(cleanStatus, Enums.OrderStatus.values(), "status"); where += " and o.status=?"; args.add(cleanStatus); }
        long total = jdbc.queryForObject("select count(*) from orders o" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(safeSize); pageArgs.add(offset);
        List<OrderRow> rows = jdbc.query("select o.public_id,o.order_number,o.customer_name,o.customer_email,o.customer_phone,o.total_minor_units,o.currency,o.status,coalesce(p.status::text,''),coalesce((select count(*) from tickets t where t.order_id=o.id),0),o.created_at " +
                "from orders o left join payments p on p.order_id=o.id" + where + " order by o.created_at desc, o.id desc limit ? offset ?", (rs, rowNum) ->
                new OrderRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6),
                        rs.getString(7), rs.getString(8), rs.getString(9), rs.getInt(10), rs.getTimestamp(11).toInstant()), pageArgs.toArray());
        return new OperationsPage<>(e.getPublicId(), e.getName(), rows, safePage, safeSize, total, total == 0 ? 0 : (int)Math.ceil(total/(double)safeSize));
    }

    public record OperationsSummary(UUID eventId, String eventName, long ticketsSold, long ticketsCheckedIn, long revenueMinor, long orderCount) {}

    public OperationsSummary operationsSummary(UUID eventPublicId, UserPrincipal actor) {
        Event e = requireEvent(eventPublicId, actor);
        return jdbc.queryForObject(
                "select e.public_id,e.name,"
                + "coalesce((select sum(tt.sold_quantity)::bigint from ticket_types tt where tt.event_id=e.id),0),"
                + "coalesce((select count(*)::bigint from tickets t where t.event_id=e.id and t.status='CHECKED_IN'),0),"
                + "coalesce((select sum(p.amount_minor)::bigint from payments p join orders o on o.id=p.order_id where o.event_id=e.id and p.status in ('CAPTURED','COMPLETED')),0),"
                + "coalesce((select count(*)::bigint from orders o2 where o2.event_id=e.id),0) "
                + "from events e where e.id=?",
                (rs,n)->new OperationsSummary(rs.getObject(1,UUID.class),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6)),
                e.getId());
    }

    /** Cursor APIs are additive. Existing page/offset APIs remain unchanged for backward compatibility. */
    public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext, int size) {}
    private record Cursor(long epochMillis, long id) {}
    private record TimedId<T>(T value, Instant time, long id) {}

    public CursorPage<ScopedTicketRow> scopedTicketsCursor(UserPrincipal actor, UUID eventPublicId, String q, String status, String source, String cursor, int size) {
        int safeSize=Math.min(MAX_SIZE,Math.max(1,size));
        if(actor==null||actor.userId()==null)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Not authorized");
        List<Object> args=new ArrayList<>();String where=" where 1=1";
        if("ORGANIZER".equals(actor.role())||"FINANCE".equals(actor.role())){where+=" and exists (select 1 from organizer_members om where om.organizer_id=e.organizer_id and om.user_id=? and om.role in ('OWNER','ORGANIZER','FINANCE'))";args.add(actor.userId());}
        else if("EVENT_MANAGER".equals(actor.role())){where+=" and exists (select 1 from event_manager_assignments ema where ema.event_id=e.id and ema.user_id=?)";args.add(actor.userId());}
        else if(!"ADMIN".equals(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Not authorized to view issued tickets");
        if(eventPublicId!=null){where+=" and e.public_id=?";args.add(eventPublicId);}String cq=cleanQuery(q);if(!cq.isBlank()){where+=" and (t.ticket_number ilike ? or coalesce(t.attendee_name,'') ilike ? or coalesce(o.customer_email,'') ilike ? or o.order_number ilike ? or e.name ilike ? or e.slug ilike ?)";String pat="%"+cq+"%";for(int i=0;i<6;i++)args.add(pat);}String st=normalize(status);if(!st.isBlank()){validateEnum(st,Enums.TicketStatus.values(),"status");where+=" and t.status=?";args.add(st);}String src=normalize(source);if(!src.isBlank()){validateEnum(src,Enums.TicketSource.values(),"source");where+=" and t.source=?";args.add(src);}Cursor c=decodeCursor(cursor);if(c!=null){where+=" and (t.created_at < to_timestamp(? / 1000.0) or (t.created_at = to_timestamp(? / 1000.0) and t.id < ?))";args.add(c.epochMillis());args.add(c.epochMillis());args.add(c.id());}
        List<Object> qargs=new ArrayList<>(args);qargs.add(safeSize+1);
        List<TimedId<ScopedTicketRow>> rows=jdbc.query("select e.public_id,e.name,e.slug,t.public_id,t.ticket_number,t.attendee_name,o.customer_email,o.customer_phone,tt.name,oi.unit_price_minor,o.currency,t.status,t.source,o.order_number,coalesce(u.full_name,''),t.created_at,t.checked_in_at,t.id from tickets t join events e on e.id=t.event_id join orders o on o.id=t.order_id join order_items oi on oi.id=t.order_item_id join ticket_types tt on tt.id=t.ticket_type_id left join users u on u.id=t.issued_by_user_id"+where+" order by t.created_at desc,t.id desc limit ?",(rs,n)->new TimedId<>(new ScopedTicketRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getLong(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15),rs.getTimestamp(16).toInstant(),rs.getTimestamp(17)==null?null:rs.getTimestamp(17).toInstant()),rs.getTimestamp(16).toInstant(),rs.getLong(18)),qargs.toArray());
        boolean hasNext=rows.size()>safeSize;if(hasNext)rows=new ArrayList<>(rows.subList(0,safeSize));String next=hasNext?encodeCursor(rows.get(rows.size()-1).time(),rows.get(rows.size()-1).id()):null;return new CursorPage<>(rows.stream().map(TimedId::value).toList(),next,hasNext,safeSize);
    }

    public CursorPage<TicketRow> ticketsCursor(UUID eventPublicId, UserPrincipal actor, String q, String status, String source, String cursor, int size){
        Event e=requireEvent(eventPublicId,actor);int safeSize=Math.min(MAX_SIZE,Math.max(1,size));List<Object> args=new ArrayList<>();String where=" where t.event_id=?";args.add(e.getId());String cq=cleanQuery(q);if(!cq.isBlank()){where+=" and (t.ticket_number ilike ? or coalesce(t.attendee_name,'') ilike ? or coalesce(o.customer_email,'') ilike ? or o.order_number ilike ?)";String pat="%"+cq+"%";for(int i=0;i<4;i++)args.add(pat);}String st=normalize(status);if(!st.isBlank()){validateEnum(st,Enums.TicketStatus.values(),"status");where+=" and t.status=?";args.add(st);}String src=normalize(source);if(!src.isBlank()){validateEnum(src,Enums.TicketSource.values(),"source");where+=" and t.source=?";args.add(src);}Cursor c=decodeCursor(cursor);if(c!=null){where+=" and (t.created_at < to_timestamp(? / 1000.0) or (t.created_at = to_timestamp(? / 1000.0) and t.id < ?))";args.add(c.epochMillis());args.add(c.epochMillis());args.add(c.id());}List<Object> qargs=new ArrayList<>(args);qargs.add(safeSize+1);List<TimedId<TicketRow>> rows=jdbc.query("select t.public_id,t.ticket_number,t.attendee_name,o.customer_email,o.customer_phone,tt.name,oi.unit_price_minor,o.currency,t.status,t.source,o.order_number,coalesce(u.full_name,''),t.created_at,t.checked_in_at,t.id from tickets t join orders o on o.id=t.order_id join order_items oi on oi.id=t.order_item_id join ticket_types tt on tt.id=t.ticket_type_id left join users u on u.id=t.issued_by_user_id"+where+" order by t.created_at desc,t.id desc limit ?",(rs,n)->new TimedId<>(new TicketRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getTimestamp(13).toInstant(),rs.getTimestamp(14)==null?null:rs.getTimestamp(14).toInstant()),rs.getTimestamp(13).toInstant(),rs.getLong(15)),qargs.toArray());boolean hasNext=rows.size()>safeSize;if(hasNext)rows=new ArrayList<>(rows.subList(0,safeSize));String next=hasNext?encodeCursor(rows.get(rows.size()-1).time(),rows.get(rows.size()-1).id()):null;return new CursorPage<>(rows.stream().map(TimedId::value).toList(),next,hasNext,safeSize);
    }

    public CursorPage<OrderRow> ordersCursor(UUID eventPublicId, UserPrincipal actor, String q, String status, String cursor, int size){
        Event e=requireEvent(eventPublicId,actor);int safeSize=Math.min(MAX_SIZE,Math.max(1,size));List<Object> args=new ArrayList<>();String where=" where o.event_id=?";args.add(e.getId());String cq=cleanQuery(q);if(!cq.isBlank()){where+=" and (o.order_number ilike ? or o.customer_name ilike ? or o.customer_email ilike ?)";String pat="%"+cq+"%";for(int i=0;i<3;i++)args.add(pat);}String st=normalize(status);if(!st.isBlank()){validateEnum(st,Enums.OrderStatus.values(),"status");where+=" and o.status=?";args.add(st);}Cursor c=decodeCursor(cursor);if(c!=null){where+=" and (o.created_at < to_timestamp(? / 1000.0) or (o.created_at = to_timestamp(? / 1000.0) and o.id < ?))";args.add(c.epochMillis());args.add(c.epochMillis());args.add(c.id());}List<Object> qargs=new ArrayList<>(args);qargs.add(safeSize+1);List<TimedId<OrderRow>> rows=jdbc.query("select o.public_id,o.order_number,o.customer_name,o.customer_email,o.customer_phone,o.total_minor_units,o.currency,o.status,coalesce(p.status::text,''),coalesce((select count(*) from tickets t where t.order_id=o.id),0),o.created_at,o.id from orders o left join payments p on p.order_id=o.id"+where+" order by o.created_at desc,o.id desc limit ?",(rs,n)->new TimedId<>(new OrderRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getInt(10),rs.getTimestamp(11).toInstant()),rs.getTimestamp(11).toInstant(),rs.getLong(12)),qargs.toArray());boolean hasNext=rows.size()>safeSize;if(hasNext)rows=new ArrayList<>(rows.subList(0,safeSize));String next=hasNext?encodeCursor(rows.get(rows.size()-1).time(),rows.get(rows.size()-1).id()):null;return new CursorPage<>(rows.stream().map(TimedId::value).toList(),next,hasNext,safeSize);
    }

    private static String encodeCursor(Instant time,long id){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((time.toEpochMilli()+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static Cursor decodeCursor(String raw){if(raw==null||raw.isBlank())return null;try{String s=new String(java.util.Base64.getUrlDecoder().decode(raw),java.nio.charset.StandardCharsets.UTF_8);String[] p=s.split(":",2);if(p.length!=2)throw new IllegalArgumentException();long t=Long.parseLong(p[0]),id=Long.parseLong(p[1]);if(t<=0||id<=0)throw new IllegalArgumentException();return new Cursor(t,id);}catch(Exception ex){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","Cursor is invalid");}}

    private Event requireEvent(UUID id, UserPrincipal actor) {
        Event e = events.findByPublicId(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!access.canManageEvent(e.getId(), actor.userId(), actor.role()))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        return e;
    }

    private static String cleanQuery(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "QUERY_TOO_LONG", "Search query is too long");
        return value;
    }

    private static String normalize(String raw) { return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT); }
    private static <E extends Enum<E>> void validateEnum(String value, E[] values, String field) { for (E e : values) if (e.name().equals(value)) return; throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_" + field.toUpperCase(Locale.ROOT), "Unsupported " + field); }
}
