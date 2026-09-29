package com.neelastack.lakhdatar.service;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckInService {
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
    public record ScanResult(Enums.CheckInResult result, String message, Ticket ticket, String ticketType, String ticketSource, String issuedByName) {}
    public record ScanResponse(String result,String message,String ticketNumber,String attendeeName,String ticketType,Instant checkedInAt,String ticketSource,String issuedByName) {}

    @Transactional
    public ScanResult scan(ScanRequest r) {
        if (r.staffUserId() == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Staff authentication required");
        if (!limits.allow("scan:" + r.staffUserId(), props.rateLimit().scanPerMinute(), java.time.Duration.ofMinutes(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many scan requests");

        if (r.eventId() == null) return record(Enums.CheckInResult.INVALID, "Invalid event", null, r, null);
        Event e = events.findByPublicId(r.eventId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        // Authorization must happen before event-state/gate validation so an unauthorized manager
        // cannot probe another manager's event state through differential responses.
        if (!access.canAccessEventAndGate(e, r.staffUserId(), r.staffRole(), r.gate()))
            return record(Enums.CheckInResult.STAFF_NOT_ASSIGNED, "Staff member is not assigned to this event", null, r, e.getId());
        Instant now = Instant.now();
        if (e.getStatus() != Enums.EventStatus.PUBLISHED || now.isBefore(e.getStartsAt().minus(earlyCheckInWindow)) || (e.getEndsAt() != null && !e.getEndsAt().isAfter(now)))
            return record(Enums.CheckInResult.EVENT_CLOSED, "Event is not currently open for entry", null, r, e.getId());
        if (r.gate() == null || r.gate().isBlank() || r.gate().length() > 80)
            return record(Enums.CheckInResult.INVALID, "Invalid gate", null, r, e.getId());
        if (r.credential() == null || r.credential().length() > 300)
            return record(Enums.CheckInResult.INVALID, "Invalid ticket", null, r, e.getId());

        UUID ticketId = qr.parseTicketId(r.credential());
        if (ticketId == null) return record(Enums.CheckInResult.INVALID, "Invalid ticket", null, r, e.getId());
        Optional<Ticket> op = tickets.findByPublicIdForUpdate(ticketId);
        if (op.isEmpty()) return record(Enums.CheckInResult.INVALID, "Invalid ticket", null, r, e.getId());
        Ticket t = op.get();
        if (!qr.verifyCredential(r.credential(), t.getPublicId(), t.getQrCredentialHash()))
            return record(Enums.CheckInResult.INVALID, "Invalid ticket credential", t, r, e.getId());
        if (!t.getEventId().equals(e.getId()))
            return record(Enums.CheckInResult.WRONG_EVENT, "Ticket belongs to a different event", t, r, e.getId());

        return switch (t.getStatus()) {
            case CANCELLED -> record(Enums.CheckInResult.CANCELLED, "Ticket was cancelled", t, r, e.getId());
            case REFUNDED -> record(Enums.CheckInResult.REFUNDED, "Ticket was refunded", t, r, e.getId());
            case CHECKED_IN -> record(Enums.CheckInResult.ALREADY_USED, "Ticket already checked in", t, r, e.getId());
            case ISSUED -> {
                t.setStatus(Enums.TicketStatus.CHECKED_IN);
                t.setCheckedInAt(Instant.now());
                yield record(Enums.CheckInResult.ACCEPTED, "Entry accepted", t, r, e.getId());
            }
        };
    }

    private ScanResult record(Enums.CheckInResult result, String msg, Ticket t, ScanRequest r, Long eventId) {
        TicketCheckin c = new TicketCheckin();
        c.setEventId(eventId);
        c.setTicketId(t == null ? null : t.getId());
        c.setStaffUserId(r.staffUserId());
        c.setGate(r.gate());
        c.setResult(result);
        c.setCorrelationId(r.correlationId());
        checkins.save(c);
        audit.log(r.staffUserId(), result == Enums.CheckInResult.ACCEPTED ? "CHECKIN_ACCEPTED" : "CHECKIN_REJECTED", "TICKET", t == null ? "unknown" : t.getPublicId().toString(), r.correlationId());
        String ticketType = t == null ? null : ticketTypes.findById(t.getTicketTypeId()).map(TicketType::getName).orElse(null);
        String issuer = t != null && t.getIssuedByUserId() != null ? users.findById(t.getIssuedByUserId()).map(User::getFullName).orElse(null) : null;
        String source = t == null || t.getSource() == null ? null : t.getSource().name();
        return new ScanResult(result, msg, t, ticketType, source, issuer);
    }
}
