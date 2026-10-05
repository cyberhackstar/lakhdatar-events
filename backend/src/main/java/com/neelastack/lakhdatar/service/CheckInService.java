package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.EventStaffRepository;
import com.neelastack.lakhdatar.repository.TicketCheckinRepository;
import com.neelastack.lakhdatar.repository.TicketRepository;
import com.neelastack.lakhdatar.repository.TicketTypeRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInService {
    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);
    private final TicketRepository tickets;
    private final TicketTypeRepository ticketTypes;
    private final UserRepository users;
    private final TicketCheckinRepository checkins;
    private final EventRepository events;
    private final EventStaffRepository staff;
    private final StaffAccessService access;
    private final QrCredentialService qr;
    private final AuditService audit;
    private final RateLimitService limits;
    private final AppProperties props;
    @Value("${app.checkin.early-window:30m}") private java.time.Duration earlyCheckInWindow;

    public record ScanRequest(String credential, UUID eventId, Long staffUserId, String staffRole, String gate, String correlationId) {}
    public record ScanResult(Enums.CheckInResult result, String message, Ticket ticket, String ticketType, String ticketSource, String issuedByName, int ticketPosition, long orderTicketCount) {}
    public record ScanResponse(String result,String message,String ticketNumber,String attendeeName,String ticketType,Instant checkedInAt,String ticketSource,String issuedByName,int ticketPosition,long orderTicketCount) {}

    @Transactional
    public ScanResult scan(ScanRequest r) {
        long started = System.nanoTime();
        if (r.staffUserId() == null) { EnterpriseLog.warn(log, "checkin.rejected", "event.category", "checkin", "result", "UNAUTHORIZED"); throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Staff authentication required"); }
        if (!limits.allowFailOpen("scan:" + r.staffUserId(), props.rateLimit().scanPerMinute(), java.time.Duration.ofMinutes(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many scan requests");

        if (r.eventId() == null) return record(Enums.CheckInResult.INVALID, "Invalid event", null, r, null, 0, 0);
        Event e = events.findByPublicId(r.eventId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        // Authorization must happen before event-state/gate validation so an unauthorized manager
        // cannot probe another manager's event state through differential responses.
        if (!access.canAccessEventAndGate(e, r.staffUserId(), r.staffRole(), r.gate()))
            return record(Enums.CheckInResult.STAFF_NOT_ASSIGNED, "Staff member is not assigned to this event", null, r, e.getId(), 0, 0);
        Instant now = Instant.now();
        if (e.getStatus() != Enums.EventStatus.PUBLISHED || now.isBefore(e.getStartsAt().minus(earlyCheckInWindow)) || (e.getEndsAt() != null && !e.getEndsAt().isAfter(now)))
            return record(Enums.CheckInResult.EVENT_CLOSED, "Event is not currently open for entry", null, r, e.getId(), 0, 0);
        if (r.gate() == null || r.gate().isBlank() || r.gate().length() > 80)
            return record(Enums.CheckInResult.INVALID, "Invalid gate", null, r, e.getId(), 0, 0);
        if (r.credential() == null || r.credential().length() > 300)
            return record(Enums.CheckInResult.INVALID, "Invalid ticket", null, r, e.getId(), 0, 0);

        UUID ticketId = qr.parseTicketId(r.credential());
        if (ticketId == null) return record(Enums.CheckInResult.INVALID, "Invalid ticket", null, r, e.getId(), 0, 0);
        Optional<Ticket> op = tickets.findByPublicIdForUpdate(ticketId);
        if (op.isEmpty()) return record(Enums.CheckInResult.INVALID, "Invalid ticket", null, r, e.getId(), 0, 0);
        Ticket t = op.get();
        if (!qr.verifyCredential(r.credential(), t.getPublicId(), t.getQrCredentialHash()))
            return record(Enums.CheckInResult.INVALID, "Invalid ticket credential", t, r, e.getId(), 0, 0, false);
        if (!t.getEventId().equals(e.getId()))
            return record(Enums.CheckInResult.WRONG_EVENT, "Ticket belongs to a different event", t, r, e.getId(), 0, 0, false);

        List<Ticket> orderTickets = tickets.findByOrderIdOrderByTicketNumberAsc(t.getOrderId());
        long orderTicketCount = orderTickets.size();
        int ticketPosition = 0;
        for (int i = 0; i < orderTickets.size(); i++) {
            if (orderTickets.get(i).getId().equals(t.getId())) { ticketPosition = i + 1; break; }
        }
        if (ticketPosition == 0) ticketPosition = 1;

        ScanResult result = switch (t.getStatus()) {
            case CANCELLED -> record(Enums.CheckInResult.CANCELLED, "Ticket was cancelled", t, r, e.getId(), ticketPosition, orderTicketCount);
            case REFUNDED -> record(Enums.CheckInResult.REFUNDED, "Ticket was refunded", t, r, e.getId(), ticketPosition, orderTicketCount);
            case CHECKED_IN -> record(Enums.CheckInResult.ALREADY_USED, "Ticket already checked in", t, r, e.getId(), ticketPosition, orderTicketCount);
            case ISSUED -> {
                t.setStatus(Enums.TicketStatus.CHECKED_IN);
                t.setCheckedInAt(Instant.now());
                yield record(Enums.CheckInResult.ACCEPTED, "Entry accepted", t, r, e.getId(), ticketPosition, orderTicketCount);
            }
        };
        EnterpriseLog.info(log, "checkin.completed", "event.category", "checkin", "result", result.result().name(), "ticket.id", t.getPublicId(), "event.id", r.eventId(), "staff.user_id", r.staffUserId(), "gate", r.gate(), "order.ticket_count", result.orderTicketCount(), "duration.ms", (System.nanoTime()-started)/1_000_000L);
        return result;
    }

    private ScanResult record(Enums.CheckInResult result, String msg, Ticket t, ScanRequest r, Long eventId, int ticketPosition, long orderTicketCount) {
        return record(result, msg, t, r, eventId, ticketPosition, orderTicketCount, true);
    }

    private ScanResult record(Enums.CheckInResult result, String msg, Ticket t, ScanRequest r, Long eventId, int ticketPosition, long orderTicketCount, boolean exposeTicket) {
        TicketCheckin c = new TicketCheckin();
        c.setEventId(eventId);
        c.setTicketId(t == null ? null : t.getId());
        c.setStaffUserId(r.staffUserId());
        c.setGate(r.gate());
        c.setResult(result);
        c.setCorrelationId(r.correlationId());
        checkins.save(c);
        EnterpriseLog.debug(log, "checkin.recorded", "event.category", "checkin", "result", result.name(), "event.id", eventId, "staff.user_id", r.staffUserId(), "gate", r.gate(), "correlation.id", r.correlationId());
        audit.log(r.staffUserId(), result == Enums.CheckInResult.ACCEPTED ? "CHECKIN_ACCEPTED" : "CHECKIN_REJECTED", "TICKET", t == null ? "unknown" : t.getPublicId().toString(), r.correlationId());
        if (!exposeTicket) return new ScanResult(result, msg, null, null, null, null, 0, 0);
        String ticketType = t == null ? null : ticketTypes.findById(t.getTicketTypeId()).map(TicketType::getName).orElse(null);
        String issuer = t != null && t.getIssuedByUserId() != null ? users.findById(t.getIssuedByUserId()).map(User::getFullName).orElse(null) : null;
        String source = t == null || t.getSource() == null ? null : t.getSource().name();
        return new ScanResult(result, msg, t, ticketType, source, issuer, ticketPosition, orderTicketCount);
    }
}
