package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final TicketReservationService reservationService;
    private final EventRepository events;
    private final OrganizerMemberRepository members;
    private final RazorpayService razorpay;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final DistributedLockService locks;
    private final EventAccessService eventAccess;

    public RefundResult refund(UUID paymentPublicId, String reason, Long actorId, String role) {
        PreparedRefund prepared = tx.execute(status -> prepareRefund(paymentPublicId, reason, actorId, role, false));
        if (prepared == null) throw new ApiException(HttpStatus.CONFLICT,"REFUND_FAILED","Refund could not be prepared");
        if (prepared.alreadyQueued()) return new RefundResult(prepared.paymentPublicId(), prepared.refundPublicId().toString(), prepared.amountMinor(), "PROCESSING");
        processRefund(prepared.refundId());
        Refund r = refunds.findById(prepared.refundId()).orElseThrow();
        return new RefundResult(prepared.paymentPublicId(), r.getRazorpayRefundId(), r.getAmountMinor(), r.getStatus().name());
    }

    PreparedRefund queueCapturedPaymentRefund(Long paymentId, String reason) {
        PreparedRefund prepared = tx.execute(status -> {
            Payment p = payments.findById(paymentId).orElseThrow();
            return prepareRefund(p.getPublicId(), reason, null, "SYSTEM", true);
        });
        if (prepared != null && !prepared.alreadyQueued()) processRefund(prepared.refundId());
        return prepared;
    }

    /** Queue an automatic system refund without calling the payment provider synchronously. */
    PreparedRefund queueCapturedPaymentRefundOnly(Long paymentId, String reason) {
        return tx.execute(status -> {
            Payment p = payments.findById(paymentId).orElseThrow();
            return prepareRefund(p.getPublicId(), reason, null, "SYSTEM", true);
        });
    }

    private PreparedRefund prepareRefund(UUID paymentPublicId, String reason, Long actorId, String role, boolean allowSystem) {
        String normalizedReason = reason == null ? "Event cancellation" : reason.trim();
        if (normalizedReason.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_REASON","Refund reason is too long");
        Payment p=payments.findByPublicId(paymentPublicId).flatMap(found -> payments.findByIdForUpdate(found.getId())).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment not found"));
        if (p.getStatus() != Enums.PaymentStatus.CAPTURED && p.getStatus() != Enums.PaymentStatus.COMPLETED && p.getStatus() != Enums.PaymentStatus.REFUND_PENDING)
            throw new ApiException(HttpStatus.CONFLICT,"REFUND_NOT_ALLOWED","Payment is not captured");
        Order o = orders.findById(p.getOrderId()).orElseThrow();
        Event event = events.findById(o.getEventId()).orElseThrow();
        if (!allowSystem && !"ADMIN".equals(role)) {
            // EVENT_MANAGER is deliberately not a financial role. Event scope grants operational
            // control over the assigned event, but refunds remain ADMIN/FINANCE/ORGANIZER-owned.
            if (!isRefundManager(event.getOrganizerId(), actorId))
                throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Not authorized to refund this payment");
        }

        var existing = refunds.findByPaymentId(p.getId());
        if (existing.isPresent()) {
            Refund r = existing.get();
            if (r.getStatus() == Enums.RefundStatus.COMPLETED) throw new ApiException(HttpStatus.CONFLICT,"REFUND_EXISTS","A refund already exists for this payment");
            if (r.getStatus() == Enums.RefundStatus.FAILED) {
                r.setStatus(Enums.RefundStatus.PROCESSING);
                r.setLastError(null);
                r.setLastAttemptAt(null);
                return new PreparedRefund(p.getPublicId(), r.getId(), r.getPublicId(), r.getAmountMinor(), false);
            }
            return new PreparedRefund(p.getPublicId(), r.getId(), r.getPublicId(), r.getAmountMinor(), true);
        }

        boolean checked = tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream().anyMatch(t -> t.getStatus() == Enums.TicketStatus.CHECKED_IN);
        if (checked) throw new ApiException(HttpStatus.CONFLICT,"REFUND_NOT_ALLOWED","A ticket has already been checked in");

        Refund r = new Refund();
        r.setPaymentId(p.getId()); r.setAmountMinor(p.getAmountMinor()); r.setReason(normalizedReason);
        r.setStatus(Enums.RefundStatus.PROCESSING); r.setAttemptCount(0); refunds.saveAndFlush(r);
        p.setStatus(Enums.PaymentStatus.REFUND_PENDING);
        o.setStatus(Enums.OrderStatus.CANCELLED);
        tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).forEach(t -> { if (t.getStatus() != Enums.TicketStatus.CHECKED_IN) t.setStatus(Enums.TicketStatus.CANCELLED); });
        // Release any reservation that is still held so cancelled/refunded orders immediately return inventory.
        reservationService.releaseOrder(o.getId());
        audit.log(actorId, "REFUND_QUEUED", "PAYMENT", p.getPublicId().toString(), null);
        return new PreparedRefund(p.getPublicId(), r.getId(), r.getPublicId(), r.getAmountMinor(), false);
    }

    public void processRefund(Long refundId) {
        Refund pending = refunds.findById(refundId).orElse(null);
        if (pending == null || pending.getStatus() == Enums.RefundStatus.COMPLETED) return;
        Payment payment = payments.findById(pending.getPaymentId()).orElse(null);
        if (payment == null || payment.getRazorpayPaymentId() == null) return;
        var lock = locks.tryAcquire("refund:" + payment.getId(), java.time.Duration.ofSeconds(90));
        if (!lock.acquired()) return;
        try {
            finalizeFromProvider(pending.getId(), payment.getRazorpayPaymentId());
        } finally { lock.close(); }
    }

    private void finalizeFromProvider(Long refundId, String providerPaymentId) {
        Refund pending = refunds.findById(refundId).orElse(null);
        if (pending == null || pending.getStatus() == Enums.RefundStatus.COMPLETED) return;
        try {
            var providerPayment = razorpay.fetchPayment(providerPaymentId);
            long alreadyRefunded = Math.max(0, providerPayment.amountRefunded());
            long remaining = pending.getAmountMinor() - alreadyRefunded;
            RazorpayService.ProviderRefund providerRefund = null;
            String receipt = pending.getProviderReceipt();
            if (remaining > 0) {
                String cleanedId = pending.getPublicId().toString().replace("-", "");
                String desiredReceipt = "LKRF-" + cleanedId.substring(0, 16) + "-" + remaining;
                String idempotencyKey = "LKREF-" + cleanedId.substring(0, 24) + "-" + remaining;
                if (!desiredReceipt.equals(receipt)) {
                    final String newReceipt = desiredReceipt;
                    tx.executeWithoutResult(status -> refunds.findById(refundId).ifPresent(r -> r.setProviderReceipt(newReceipt)));
                    receipt = desiredReceipt;
                }
                try {
                    final String receiptForLookup = receipt;
                    var existing = razorpay.fetchRefundsForPayment(providerPaymentId).stream()
                            .filter(r -> receiptForLookup.equals(r.receipt()))
                            .filter(r -> r.amount() == remaining)
                            .findFirst();
                    if (existing.isPresent()) {
                        providerRefund = existing.get();
                    } else {
                        var created = razorpay.refund(providerPaymentId, remaining, pending.getReason(), receipt, idempotencyKey);
                        providerRefund = new RazorpayService.ProviderRefund(created.refundId(), created.status(), created.amount(), receipt);
                    }
                } catch (RuntimeException providerFailure) {
                    // The POST may have succeeded but the response may have been lost. Recover only an exact receipt match; amount-only matching is unsafe.
                    final String receiptForRecovery = receipt;
                    var recovered = razorpay.fetchRefundsForPayment(providerPaymentId).stream()
                            .filter(r -> receiptForRecovery.equals(r.receipt()))
                            .filter(r -> r.amount() == remaining)
                            .findFirst();
                    if (recovered.isEmpty()) throw providerFailure;
                    providerRefund = recovered.get();
                }
            } else if (pending.getRazorpayRefundId() != null) {
                providerRefund = razorpay.fetchRefund(providerPaymentId, pending.getRazorpayRefundId()).orElse(null);
            } else if (pending.getProviderReceipt() != null) {
                final String receiptForRecovery = pending.getProviderReceipt();
                providerRefund = razorpay.fetchRefundsForPayment(providerPaymentId).stream()
                        .filter(r -> receiptForRecovery.equals(r.receipt()))
                        .filter(r -> r.amount() == pending.getAmountMinor())
                        .filter(r -> !"failed".equalsIgnoreCase(r.status()))
                        .findFirst().orElse(null);
            }

            final RazorpayService.ProviderRefund result = providerRefund;
            final var latestProviderPayment = razorpay.fetchPayment(providerPaymentId);
            final long providerAmountRefunded = latestProviderPayment.amountRefunded();
            final String providerPaymentStatus = latestProviderPayment.status();
            tx.executeWithoutResult(status -> {
                Refund r = refunds.findById(refundId).orElseThrow();
                r.setProviderStatus(result == null ? "pending" : result.status());
                r.setRazorpayRefundId(result == null ? r.getRazorpayRefundId() : result.id());
                if (result != null && result.receipt() != null) r.setProviderReceipt(result.receipt());
                r.setLastError(null);
                r.setLastAttemptAt(Instant.now());
                r.setAttemptCount(r.getAttemptCount() + 1);

                boolean fullyRefunded = providerAmountRefunded >= r.getAmountMinor();
                boolean providerProcessed = result != null && ("processed".equalsIgnoreCase(result.status()) || "completed".equalsIgnoreCase(result.status()));
                boolean failed = result != null && "failed".equalsIgnoreCase(result.status());
                if (fullyRefunded && (providerProcessed || "refunded".equalsIgnoreCase(providerPaymentStatus))) {
                    r.setStatus(Enums.RefundStatus.COMPLETED);
                    Payment p = payments.findById(r.getPaymentId()).orElseThrow();
                    p.setStatus(Enums.PaymentStatus.REFUNDED);
                    orders.findById(p.getOrderId()).ifPresent(o -> o.setStatus(Enums.OrderStatus.CANCELLED));
                    tickets.findByOrderIdOrderByTicketNumberAsc(p.getOrderId()).forEach(t -> { if (t.getStatus() != Enums.TicketStatus.CHECKED_IN) t.setStatus(Enums.TicketStatus.REFUNDED); });
                    audit.log(null, "REFUND_COMPLETED", "PAYMENT", p.getPublicId().toString(), null);
                    return;
                }
                if (failed) r.setLastError("Provider reported refund failure; recovery will retry safely");
                r.setStatus(Enums.RefundStatus.PROCESSING);
                payments.findById(r.getPaymentId()).ifPresent(p -> p.setStatus(Enums.PaymentStatus.REFUND_PENDING));
            });
        } catch (Exception ex) {
            tx.executeWithoutResult(status -> refunds.findById(refundId).ifPresent(r -> {
                r.setLastError(ex.getClass().getSimpleName());
                r.setLastAttemptAt(Instant.now());
                r.setAttemptCount(r.getAttemptCount() + 1);
                r.setStatus(Enums.RefundStatus.PROCESSING);
            }));
        }
    }

    private boolean isRefundManager(Long organizerId, Long actorId) {
        return actorId != null && members.findByOrganizerIdAndUserId(organizerId, actorId)
                .map(m -> "OWNER".equalsIgnoreCase(m.getRole()) || "FINANCE".equalsIgnoreCase(m.getRole()))
                .orElse(false);
    }

    public record PreparedRefund(UUID paymentPublicId, Long refundId, UUID refundPublicId, long amountMinor, boolean alreadyQueued) {}
    public record RefundResult(UUID paymentId, String refundId, long amountMinor, String status) {}
}
