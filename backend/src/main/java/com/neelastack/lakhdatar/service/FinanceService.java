package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.exception.ApiException;
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

/**
 * Financial/operations read model. It deliberately uses parameterised SQL and the same
 * organizer scope rules as the rest of the platform. It never exposes payment secrets.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {
    private static final int MAX_SIZE = 100;
    private static final int MAX_PAGE = 100_000;

    private final JdbcTemplate jdbc;

    public record Overview(long grossCapturedMinor, long refundedMinor, long netMinor,
                           long pendingPaymentCount, long pendingRefundCount,
                           long recoveryPendingCount, long failedMailCount, long heldReservationCount,
                           long expiredReservationCount, long webhookBacklogCount, long webhookStuckCount,
                           long stalePaymentCount, Instant oldestPendingPaymentAt) {}

    public record RefundRow(UUID refundId, UUID paymentId, String orderNumber, String customerName,
                            String eventName, long amountMinor, String currency, String status,
                            String providerStatus, Instant createdAt) {}

    public record PageView<T>(List<T> items, int page, int size, long total, int totalPages) {}
    public record LedgerRow(UUID entryId, String entryType, UUID paymentId, UUID refundId, String orderNumber, String eventName, String organizerName, long amountMinor, String currency, Instant createdAt) {}
    public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext, int size) {}
    private record Cursor(long epochMillis, long id) {}
    private record Timed<T>(T value, Instant time, long id) {}

    private record Scope(String clause, List<Object> args) {}

    public Overview overview(UserPrincipal actor) {
        Scope s = scope(actor, "e");
        String join = " from payments p join orders o on o.id=p.order_id join events e on e.id=o.event_id ";
        long gross = scalar("select coalesce(sum(p.amount_minor),0)" + join + " where p.status in ('CAPTURED','COMPLETED')" + s.clause(), s.args());
        long refunded = scalar("select coalesce(sum(r.amount_minor),0) from refunds r join payments p on p.id=r.payment_id join orders o on o.id=p.order_id join events e on e.id=o.event_id where r.status='COMPLETED'" + s.clause(), s.args());
        long pendingPayments = scalar("select count(*)" + join + " where p.status in ('CREATED','PENDING','PAYMENT_INITIATED','AUTHORIZED','REFUND_PENDING')" + s.clause(), s.args());
        long pendingRefunds = scalar("select count(*) from refunds r join payments p on p.id=r.payment_id join orders o on o.id=p.order_id join events e on e.id=o.event_id where r.status in ('REQUESTED','PROCESSING')" + s.clause(), s.args());
        long recoveryPending = scalar("select count(*)" + join + " where p.razorpay_order_state='RECOVERY_PENDING'" + s.clause(), s.args());
        long stalePayments = scalar("select count(*)" + join + " where p.status in ('CREATED','PENDING','PAYMENT_INITIATED','AUTHORIZED','REFUND_PENDING') and p.created_at < (now() - interval '2 minutes')" + s.clause(), s.args());
        long failedMail = scalar("select count(*) from ticket_mail_jobs j join orders o on o.id=j.order_id join events e on e.id=o.event_id where j.status='FAILED'" + s.clause(), s.args());
        long heldReservations = scalar("select count(*) from ticket_reservations tr join ticket_types tt on tt.id=tr.ticket_type_id join events e on e.id=tt.event_id where tr.status='HELD' and tr.expires_at > now()" + s.clause(), s.args());
        long expiredReservations = scalar("select count(*) from ticket_reservations tr join ticket_types tt on tt.id=tr.ticket_type_id join events e on e.id=tt.event_id where tr.status='HELD' and tr.expires_at <= now()" + s.clause(), s.args());
        long webhookBacklog = "ADMIN".equalsIgnoreCase(actor.role()) ? scalar("select count(*) from payment_webhook_events w where w.processed=false and w.processing=false and w.received_at < (now() - interval '2 minutes')", List.of()) : 0L;
        long webhookStuck = "ADMIN".equalsIgnoreCase(actor.role()) ? scalar("select count(*) from payment_webhook_events w where w.processing=true and w.processing_started_at < (now() - interval '5 minutes')", List.of()) : 0L;
        Instant oldest = jdbc.query("select min(p.created_at)" + join + " where p.status in ('CREATED','PENDING','PAYMENT_INITIATED','AUTHORIZED','REFUND_PENDING') and p.created_at < (now() - interval '2 minutes')" + s.clause(), s.args().toArray(), rs -> rs.next() ? rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant() : null);
        return new Overview(gross, refunded, gross - refunded, pendingPayments, pendingRefunds, recoveryPending, failedMail, heldReservations, expiredReservations, webhookBacklog, webhookStuck, stalePayments, oldest);
    }

    public PageView<RefundRow> refunds(UserPrincipal actor, String q, String status, int page, int size) {
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        if (page < 0 || page > MAX_PAGE) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page is out of range");
        Scope s = scope(actor, "e");
        List<Object> args = new ArrayList<>(s.args());
        String where = " where 1=1" + s.clause();
        String cleanQ = clean(q);
        if (!cleanQ.isBlank()) {
            where += " and (r.provider_refund_id ilike ? or r.public_id::text ilike ? or o.order_number ilike ? or coalesce(o.customer_name,'') ilike ? or coalesce(o.customer_email,'') ilike ? or e.name ilike ?)";
            String p = "%" + cleanQ + "%";
            for (int i = 0; i < 6; i++) args.add(p);
        }
        String cleanStatus = clean(status).toUpperCase(Locale.ROOT);
        if (!cleanStatus.isBlank()) {
            try { com.neelastack.lakhdatar.domain.Enums.RefundStatus.valueOf(cleanStatus); }
            catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown refund status"); }
            where += " and r.status=?"; args.add(cleanStatus);
        }
        String from = " from refunds r join payments p on p.id=r.payment_id join orders o on o.id=p.order_id join events e on e.id=o.event_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, args.toArray(), Long.class);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(page * safeSize);
        List<RefundRow> rows = jdbc.query("select r.public_id, p.public_id, o.order_number, o.customer_name, e.name, r.amount_minor, p.currency, r.status, r.provider_status, r.created_at" + from + where + " order by r.created_at desc, r.id desc limit ? offset ?", pageArgs.toArray(), (rs, n) ->
                new RefundRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getTimestamp(10).toInstant()));
        long count = total == null ? 0L : total;
        int pages = (int)Math.max(1L, (count + safeSize - 1L) / safeSize);
        return new PageView<>(rows, page, safeSize, count, pages);
    }


    public CursorPage<RefundRow> refundsCursor(UserPrincipal actor, String q, String status, String cursor, int size) {
        int safe = Math.min(100, Math.max(1, size));
        Scope scope = scope(actor, "e");
        List<Object> args = new ArrayList<>(scope.args());
        String where = " where 1=1" + scope.clause();
        String cleanQ = clean(q);
        if (!cleanQ.isBlank()) { where += " and (r.provider_refund_id ilike ? or r.public_id::text ilike ? or o.order_number ilike ? or coalesce(o.customer_name,'') ilike ? or coalesce(o.customer_email,'') ilike ? or e.name ilike ?)"; String p = "%"+cleanQ+"%"; for(int i=0;i<6;i++) args.add(p); }
        String cleanStatus = clean(status).toUpperCase(Locale.ROOT);
        if (!cleanStatus.isBlank()) { try { Enums.RefundStatus.valueOf(cleanStatus); } catch (IllegalArgumentException ex) { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STATUS","Unknown refund status"); } where += " and r.status=?"; args.add(cleanStatus); }
        Cursor c = decodeCursor(cursor);
        if(c!=null){ where += " and (r.created_at < to_timestamp(? / 1000.0) or (r.created_at = to_timestamp(? / 1000.0) and r.id < ?))"; args.add(c.epochMillis()); args.add(c.epochMillis()); args.add(c.id()); }
        List<Object> qargs = new ArrayList<>(args); qargs.add(safe+1);
        String from = " from refunds r join payments p on p.id=r.payment_id join orders o on o.id=p.order_id join events e on e.id=o.event_id";
        List<Timed<RefundRow>> rows = jdbc.query("select r.public_id,p.public_id,o.order_number,o.customer_name,e.name,r.amount_minor,p.currency,r.status,r.provider_status,r.created_at,r.id"+from+where+" order by r.created_at desc,r.id desc limit ?", qargs.toArray(), (rs,n)->new Timed<>(new RefundRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getTimestamp(10).toInstant()),rs.getTimestamp(10).toInstant(),rs.getLong(11)));
        boolean more=rows.size()>safe; if(more) rows=new ArrayList<>(rows.subList(0,safe)); String next=more?encodeCursor(rows.get(rows.size()-1).time(),rows.get(rows.size()-1).id()):null;
        return new CursorPage<>(rows.stream().map(Timed::value).toList(),next,more,safe);
    }

    public CursorPage<LedgerRow> ledgerCursor(UserPrincipal actor, String q, String entryType, String cursor, int size) {
        int safe=Math.min(100,Math.max(1,size)); Scope scope=scope(actor,"e"); List<Object> args=new ArrayList<>(scope.args()); String where=" where 1=1"+scope.clause(); String cq=clean(q);
        if(!cq.isBlank()){where+=" and (coalesce(o.order_number,'') ilike ? or coalesce(e.name,'') ilike ? or coalesce(org.name,'') ilike ? or l.entry_type ilike ?)";String p="%"+cq+"%";for(int i=0;i<4;i++)args.add(p);} String type=clean(entryType).toUpperCase(Locale.ROOT); if(!type.isBlank()){if(!"SALE".equals(type)&&!"REFUND".equals(type))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ENTRY_TYPE","Unsupported ledger entry type");where+=" and l.entry_type=?";args.add(type);}
        Cursor c=decodeCursor(cursor);if(c!=null){where+=" and (l.created_at < to_timestamp(? / 1000.0) or (l.created_at = to_timestamp(? / 1000.0) and l.id < ?))";args.add(c.epochMillis());args.add(c.epochMillis());args.add(c.id());}
        List<Object> qargs=new ArrayList<>(args);qargs.add(safe+1);String from=" from financial_ledger_entries l left join payments p on p.id=l.payment_id left join refunds r on r.id=l.refund_id left join orders o on o.id=l.order_id left join events e on e.id=l.event_id left join organizers org on org.id=l.organizer_id";
        List<Timed<LedgerRow>> rows=jdbc.query("select l.entry_id,l.entry_type,l.payment_id,l.refund_id,o.order_number,e.name,org.name,l.amount_minor,l.currency,l.created_at,l.id"+from+where+" order by l.created_at desc,l.id desc limit ?",qargs.toArray(),(rs,n)->new Timed<>(new LedgerRow(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getString(7),rs.getLong(8),rs.getString(9),rs.getTimestamp(10).toInstant()),rs.getTimestamp(10).toInstant(),rs.getLong(11)));
        boolean more=rows.size()>safe;if(more)rows=new ArrayList<>(rows.subList(0,safe));String next=more?encodeCursor(rows.get(rows.size()-1).time(),rows.get(rows.size()-1).id()):null;return new CursorPage<>(rows.stream().map(Timed::value).toList(),next,more,safe);
    }

    public PageView<LedgerRow> ledger(UserPrincipal actor, String q, String entryType, int page, int size) {
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        if (page < 0 || page > MAX_PAGE) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Page is out of range");
        Scope scope = scope(actor, "e");
        List<Object> args = new ArrayList<>(scope.args());
        String where = " where 1=1" + scope.clause();
        String cleanQ = clean(q);
        if (!cleanQ.isBlank()) {
            where += " and (coalesce(o.order_number,'') ilike ? or coalesce(e.name,'') ilike ? or coalesce(org.name,'') ilike ? or l.entry_type ilike ?)";
            String pattern = "%" + cleanQ + "%";
            args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
        }
        String type = clean(entryType).toUpperCase(Locale.ROOT);
        if (!type.isBlank()) {
            if (!"SALE".equals(type) && !"REFUND".equals(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ENTRY_TYPE", "Unsupported ledger entry type");
            where += " and l.entry_type=?"; args.add(type);
        }
        String from = " from financial_ledger_entries l left join payments p on p.id=l.payment_id left join refunds r on r.id=l.refund_id left join orders o on o.id=l.order_id left join events e on e.id=l.event_id left join organizers org on org.id=l.organizer_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, args.toArray(), Long.class);
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(safeSize); pageArgs.add(page * safeSize);
        List<LedgerRow> rows = jdbc.query("select l.entry_id,l.entry_type,l.payment_id,l.refund_id,o.order_number,e.name,org.name,l.amount_minor,l.currency,l.created_at" + from + where + " order by l.created_at desc,l.id desc limit ? offset ?", pageArgs.toArray(), (rs,n) -> new LedgerRow(rs.getObject(1, UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getString(7),rs.getLong(8),rs.getString(9),rs.getTimestamp(10).toInstant()));
        long count = total == null ? 0L : total;
        int pages = (int)Math.max(1L, (count + safeSize - 1L) / safeSize);
        return new PageView<>(rows,page,safeSize,count,pages);
    }

    private Scope scope(UserPrincipal actor, String eventAlias) {
        if (actor == null || actor.userId() == null) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized");
        if ("ADMIN".equalsIgnoreCase(actor.role())) return new Scope("", List.of());
        if ("ORGANIZER".equalsIgnoreCase(actor.role()) || "FINANCE".equalsIgnoreCase(actor.role())) {
            return new Scope(" and exists (select 1 from organizer_members om where om.organizer_id=" + eventAlias + ".organizer_id and om.user_id=? and om.role in ('OWNER','ORGANIZER','FINANCE'))", List.of(actor.userId()));
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Finance access is not available for this role");
    }

    private long scalar(String sql, List<Object> args) {
        Long value = jdbc.queryForObject(sql, args.toArray(), Long.class);
        return value == null ? 0L : value;
    }

    private static String encodeCursor(Instant time,long id){return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((time.toEpochMilli()+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static Cursor decodeCursor(String raw){if(raw==null||raw.isBlank())return null;try{String s=new String(java.util.Base64.getUrlDecoder().decode(raw),java.nio.charset.StandardCharsets.UTF_8);String[] p=s.split(":",2);if(p.length!=2)throw new IllegalArgumentException();long t=Long.parseLong(p[0]),id=Long.parseLong(p[1]);if(t<=0||id<=0)throw new IllegalArgumentException();return new Cursor(t,id);}catch(Exception ex){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","Cursor is invalid");}}

    private static String clean(String value) {
        if (value == null) return "";
        String s = value.trim();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
