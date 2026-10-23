package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.TicketReservation;
import com.neelastack.lakhdatar.domain.TicketType;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.OrderRepository;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.TicketReservationRepository;
import com.neelastack.lakhdatar.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketReservationService {
    private static final Logger log = LoggerFactory.getLogger(TicketReservationService.class);
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final EventRepository eventRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public TicketType reserve(Long id, int qty) {
        if (qty <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity must be positive");
        TicketType t = ticketTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        if (t.availableQuantity() < qty)
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "Not enough tickets available");
        t.setReservedQuantity(t.getReservedQuantity() + qty);
        EnterpriseLog.debug(log, "inventory.reservation.held", "event.category", "inventory", "ticket_type.id", id, "quantity", qty, "reserved.quantity", t.getReservedQuantity(), "available.quantity", t.availableQuantity());
        return t;
    }

    @Transactional
    public void confirmSale(Long id, int qty) {
        TicketType t = ticketTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        if (t.getReservedQuantity() < qty)
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_INCONSISTENT", "Reservation exceeds held inventory");
        t.setReservedQuantity(t.getReservedQuantity() - qty);
        t.setSoldQuantity(t.getSoldQuantity() + qty);
        EnterpriseLog.debug(log, "inventory.sale.confirmed", "event.category", "inventory", "ticket_type.id", id, "quantity", qty, "sold.quantity", t.getSoldQuantity(), "reserved.quantity", t.getReservedQuantity());
    }

    @Transactional
    public void releaseReservation(Long id, int qty) {
        TicketType t = ticketTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        if (t.getReservedQuantity() < qty) return;
        t.setReservedQuantity(t.getReservedQuantity() - qty);
    }

    @Transactional
    public void expireReservation(Long reservationId) {
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(r -> {
            if (r.getStatus() != Enums.ReservationStatus.HELD) return;
            releaseReservation(r.getTicketTypeId(), r.getQuantity());
            r.setStatus(Enums.ReservationStatus.EXPIRED);
            EnterpriseLog.info(log, "inventory.reservation.expired", "event.category", "inventory", "reservation.id", reservationId, "ticket_type.id", r.getTicketTypeId(), "quantity", r.getQuantity(), "order.id", r.getOrderId());
        });
    }

    @Transactional
    public void expireReservationAndOrder(Long reservationId) {
        TicketReservation initial = reservationRepository.findById(reservationId).orElse(null);
        if (initial == null) return;
        if (initial.getOrderId() != null) orderRepository.findByIdForUpdate(initial.getOrderId());
        reservationRepository.findByIdForUpdate(reservationId).ifPresent(r -> {
            if (r.getStatus() != Enums.ReservationStatus.HELD) return;
            releaseReservation(r.getTicketTypeId(), r.getQuantity());
            r.setStatus(Enums.ReservationStatus.EXPIRED);
            if (r.getOrderId() != null) {
                orderRepository.findById(r.getOrderId()).ifPresent(o -> {
                    if (o.getStatus() == Enums.OrderStatus.CREATED || o.getStatus() == Enums.OrderStatus.AWAITING_PAYMENT) { o.setStatus(Enums.OrderStatus.EXPIRED); EnterpriseLog.info(log, "order.expired_after_reservation", "event.category", "order", "order.id", o.getId(), "order.number", o.getOrderNumber(), "reservation.id", reservationId); }
                });
            }
        });
    }

    /**
     * Repairs stale open checkouts whose ticket holds have already expired or been released.
     * This intentionally does not alter the payment state: provider-side truth is reconciled
     * separately, and any late capture must enter the existing refund-compensation path.
     */
    @Transactional
    public boolean closeOpenOrderWithoutLiveReservation(Long orderId, Instant now) {
        var order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || (order.getStatus() != Enums.OrderStatus.CREATED
                && order.getStatus() != Enums.OrderStatus.AWAITING_PAYMENT)) return false;
        var payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment != null && (payment.getStatus() == Enums.PaymentStatus.CAPTURED
                || payment.getStatus() == Enums.PaymentStatus.COMPLETED
                || payment.getStatus() == Enums.PaymentStatus.REFUND_PENDING)) {
            // Captured or refund-in-flight payments must be resolved by provider reconciliation,
            // never closed as an abandoned checkout by reservation cleanup.
            return false;
        }
        List<TicketReservation> orderReservations = reservationRepository.findByOrderIdOrderByIdAsc(orderId);
        if (orderReservations.isEmpty()) return false;
        boolean live = orderReservations.stream().anyMatch(r ->
                r.getStatus() == Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(now));
        if (live) return false;

        boolean eventCancelled = eventRepository.findById(order.getEventId())
                .map(e -> e.getStatus() == Enums.EventStatus.CANCELLED).orElse(false);
        order.setStatus(eventCancelled ? Enums.OrderStatus.CANCELLED : Enums.OrderStatus.EXPIRED);
        order.setCheckoutSessionHash(null);
        EnterpriseLog.info(log, "order.closed_without_live_reservation", "event.category", "recovery",
                "order.id", orderId, "order.status", order.getStatus().name(),
                "reservation.count", orderReservations.size());
        return true;
    }

    @Transactional
    public void releaseOrder(Long orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(o -> {
            for (TicketReservation r : reservationRepository.findByOrderIdForUpdate(orderId)) {
                if (r.getStatus() == Enums.ReservationStatus.HELD) {
                    releaseReservation(r.getTicketTypeId(), r.getQuantity());
                    r.setStatus(Enums.ReservationStatus.RELEASED);
                    EnterpriseLog.debug(log, "inventory.reservation.released", "event.category", "inventory", "reservation.id", r.getId(), "order.id", orderId, "ticket_type.id", r.getTicketTypeId(), "quantity", r.getQuantity());
                }
            }
        });
    }


    @Transactional
    public int releaseHeldForEvent(Long eventId) {
        if (eventId == null) return 0;
        int changed = reservationRepository.releaseHeldForEventAndAdjustInventory(eventId);
        if (changed > 0) EnterpriseLog.info(log, "inventory.event_reservations.released", "event.category", "inventory", "event.id", eventId, "ticket_types.adjusted", changed);
        return changed;
    }

    public List<TicketReservation> forOrder(Long orderId) {
        return reservationRepository.findByOrderIdOrderByIdAsc(orderId);
    }
}
