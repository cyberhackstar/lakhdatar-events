package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.TicketReservation;
import com.neelastack.lakhdatar.domain.TicketType;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.OrderRepository;
import com.neelastack.lakhdatar.repository.TicketReservationRepository;
import com.neelastack.lakhdatar.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketReservationService {
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketReservationRepository reservationRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public TicketType reserve(Long id, int qty) {
        if (qty <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity must be positive");
        TicketType t = ticketTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));
        if (t.availableQuantity() < qty)
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "Not enough tickets available");
        t.setReservedQuantity(t.getReservedQuantity() + qty);
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
                    if (o.getStatus() == Enums.OrderStatus.CREATED || o.getStatus() == Enums.OrderStatus.AWAITING_PAYMENT) o.setStatus(Enums.OrderStatus.EXPIRED);
                });
            }
        });
    }

    @Transactional
    public void releaseOrder(Long orderId) {
        orderRepository.findByIdForUpdate(orderId).ifPresent(o -> {
            for (TicketReservation r : reservationRepository.findByOrderIdForUpdate(orderId)) {
                if (r.getStatus() == Enums.ReservationStatus.HELD) {
                    releaseReservation(r.getTicketTypeId(), r.getQuantity());
                    r.setStatus(Enums.ReservationStatus.RELEASED);
                }
            }
        });
    }

    public List<TicketReservation> forOrder(Long orderId) {
        return reservationRepository.findByOrderIdOrderByIdAsc(orderId);
    }
}
