package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagerTicketService {
    private static final int MAX_COMPLIMENTARY_PER_ISSUE = 100;

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final TicketReservationRepository reservations;
    private final TicketRepository tickets;
    private final TicketReservationService reservationService;
    private final EventAccessService eventAccess;
    private final QrCredentialService qr;
    private final AccessTokenService accessTokens;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final com.neelastack.lakhdatar.repository.UserRepository users;

    public record IssueRequest(UUID eventId, UUID ticketTypeId, int quantity,
                               String attendeeName, String attendeeEmail, String attendeePhone,
                               String idempotencyKey) {}

    public record TicketRef(UUID ticketId, String ticketNumber, String accessToken) {}

    public record IssueResponse(String orderPublicId, String orderNumber, String eventName,
                                String ticketType, int quantity, long amountMinorUnits,
                                String source, String issuedByName, List<TicketRef> tickets, String emailStatus) {
        public IssueResponse withEmailStatus(String status) {
            return new IssueResponse(orderPublicId, orderNumber, eventName, ticketType, quantity, amountMinorUnits, source, issuedByName, tickets, status);
        }
    }

    @Transactional
    public IssueResponse issue(IssueRequest r, UserPrincipal actor) {
        if (actor == null || actor.userId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        }
        if (!"EVENT_MANAGER".equals(actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an assigned event manager can issue complimentary tickets");
        }
        if (r.eventId() == null || r.ticketTypeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Event and ticket type are required");
        }
        if (r.quantity() < 1 || r.quantity() > MAX_COMPLIMENTARY_PER_ISSUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Complimentary issuance is limited to 100 tickets per request");
        }
        validateText(r.attendeeName(), "Attendee name", 2, 120);
        validateEmail(r.attendeeEmail());
        validatePhone(r.attendeePhone());
        String idem = normalizeIdempotencyKey(r.idempotencyKey());
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))", (rs, n) -> rs.getObject(1), idem);

        Optional<Order> existing = orders.findByIdempotencyKey(idem).map(existingOrder -> orders.findByIdForUpdate(existingOrder.getId()).orElse(existingOrder));
        if (existing.isPresent()) {
            Order o = existing.get();
            if (!Objects.equals(o.getUserId(), actor.userId()) || o.getTotalMinorUnits() != 0L || o.getStatus() != Enums.OrderStatus.CONFIRMED) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "This issuance request key is already used");
            }
            return response(o);
        }

        Event event = events.findByPublicId(r.eventId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(event.getId(), actor.userId(), actor.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not assigned to this event");
        }
        event = events.findByIdForUpdate(event.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (event.getStatus() == Enums.EventStatus.CANCELLED || event.getStatus() == Enums.EventStatus.COMPLETED || event.getStatus() == Enums.EventStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "EVENT_NOT_OPEN", "Complimentary tickets cannot be issued for this event state");
        }

        TicketType type = ticketTypes.findByPublicId(r.ticketTypeId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        if (!Objects.equals(type.getEventId(), event.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_TYPE", "Ticket type does not belong to this event");
        }
        if (type.getStatus() == Enums.TicketTypeStatus.CLOSED || type.getTotalQuantity() <= type.getSoldQuantity() + type.getReservedQuantity()) {
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "No complimentary tickets remain for this ticket type");
        }

        Order o = new Order();
        o.setOrderNumber("LK-MGR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        o.setEventId(event.getId());
        o.setUserId(actor.userId());
        o.setCustomerName(r.attendeeName().trim());
        o.setCustomerEmail(r.attendeeEmail().trim().toLowerCase());
        o.setCustomerPhone(r.attendeePhone() == null ? null : r.attendeePhone().trim());
        o.setTotalMinorUnits(0L);
        o.setCurrency(event.getCurrency());
        o.setStatus(Enums.OrderStatus.CREATED);
        o.setIdempotencyKey(idem);
        orders.save(o);

        reservationService.reserve(type.getId(), r.quantity());
        OrderItem oi = new OrderItem();
        oi.setOrderId(o.getId());
        oi.setTicketTypeId(type.getId());
        oi.setQuantity(r.quantity());
        oi.setUnitPriceMinor(0L);
        oi.setSubtotalMinor(0L);
        orderItems.save(oi);

        TicketReservation reservation = new TicketReservation();
        reservation.setOrderId(o.getId());
        reservation.setTicketTypeId(type.getId());
        reservation.setQuantity(r.quantity());
        reservation.setStatus(Enums.ReservationStatus.HELD);
        reservation.setExpiresAt(Instant.now().plusSeconds(60));
        reservations.save(reservation);

        reservationService.confirmSale(type.getId(), r.quantity());
        reservation.setStatus(Enums.ReservationStatus.CONFIRMED);

        for (int i = 0; i < r.quantity(); i++) {
            Ticket t = new Ticket();
            t.setOrderId(o.getId());
            t.setOrderItemId(oi.getId());
            t.setEventId(event.getId());
            t.setTicketTypeId(type.getId());
            t.setAttendeeName(r.attendeeName().trim());
            t.setTicketNumber("LK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase());
            t.setStatus(Enums.TicketStatus.ISSUED);
            t.setSource(Enums.TicketSource.COMPLIMENTARY_MANAGER);
            t.setIssuedByUserId(actor.userId());
            String credential = qr.credentialFor(t.getPublicId());
            t.setQrCredentialHash(qr.hash(credential));
            tickets.save(t);
        }
        o.setStatus(Enums.OrderStatus.CONFIRMED);
        audit.log(actor.userId(), "COMPLIMENTARY_TICKETS_ISSUED", "ORDER", o.getPublicId().toString(),
                event.getPublicId() + "|" + type.getPublicId() + "|qty=" + r.quantity());
        return response(o);
    }

    private IssueResponse response(Order o) {
        Event e = events.findById(o.getEventId()).orElseThrow();
        List<Ticket> ticketList = tickets.findByOrderIdOrderByTicketNumberAsc(o.getId());
        String ticketType = ticketList.isEmpty() ? "" : ticketTypes.findById(ticketList.get(0).getTicketTypeId()).map(TicketType::getName).orElse("");
        String issuer = o.getUserId() == null ? "Event Manager" : users.findById(o.getUserId()).map(User::getFullName).orElse("Event Manager");
        List<TicketRef> refs = ticketList.stream().map(t -> new TicketRef(t.getPublicId(), t.getTicketNumber(), accessTokens.issue(t.getPublicId()))).toList();
        return new IssueResponse(o.getPublicId().toString(), o.getOrderNumber(), e.getName(), ticketType,
                ticketList.size(), 0L, Enums.TicketSource.COMPLIMENTARY_MANAGER.name(), issuer, refs, null);
    }

    private static void validateText(String value, String label, int min, int max) {
        if (value == null || value.trim().length() < min || value.trim().length() > max)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", label + " is invalid");
    }

    private static void validateEmail(String value) {
        if (value == null || value.length() > 255 || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter a valid attendee email address");
    }

    private static void validatePhone(String value) {
        if (value == null || value.isBlank()) return;
        if (value.length() > 40 || !value.matches("^[0-9+()\\- .]{7,40}$"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a valid phone number");
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.length() < 16 || value.length() > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Issuance request key must be 16 to 100 characters");
        return value.trim();
    }
}
