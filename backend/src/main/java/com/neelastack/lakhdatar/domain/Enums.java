package com.neelastack.lakhdatar.domain;

public final class Enums {
    private Enums() {}
    public enum UserRole { ADMIN, ORGANIZER, EVENT_MANAGER, STAFF, FINANCE, SUPPORT, CUSTOMER }
    public enum EventStatus { DRAFT, PUBLISHED, UNPUBLISHED, CANCELLED, COMPLETED, ARCHIVED }
    public enum TicketTypeStatus { ACTIVE, PAUSED, CLOSED }
    public enum ReservationStatus { HELD, CONFIRMED, RELEASED, EXPIRED }
    public enum OrderStatus { CREATED, AWAITING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED }
    public enum PaymentStatus { CREATED, PENDING, PAYMENT_INITIATED, AUTHORIZED, CAPTURED, COMPLETED, FAILED, CANCELLED, REFUND_PENDING, REFUNDED }
    public enum TicketStatus { ISSUED, CANCELLED, REFUNDED, CHECKED_IN }
    public enum TicketSource { ONLINE_PAYMENT, COMPLIMENTARY_MANAGER }
    public enum CheckInResult { ACCEPTED, ALREADY_USED, INVALID, CANCELLED, REFUNDED, WRONG_EVENT, STAFF_NOT_ASSIGNED, EVENT_CLOSED }
    public enum RefundStatus { REQUESTED, PROCESSING, COMPLETED, FAILED }
}
