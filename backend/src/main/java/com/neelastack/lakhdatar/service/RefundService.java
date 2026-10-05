package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
    private final PaymentGatewayRouter gateways;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final DistributedLockService locks;
    private final EventAccessService eventAccess;
    private final PaymentStateMachine paymentStateMachine;

    public RefundResult refund(UUID paymentPublicId, String reason, Long actorId, String role) {
        PreparedRefund prepared = tx.execute(status -> prepareRefund(paymentPublicId, reason, actorId, role, false));
        if (prepared == null) throw new ApiException(HttpStatus.CONFLICT, "REFUND_FAILED", "Refund could not be prepared");
        if (prepared.alreadyQueued()) {
            return new RefundResult(prepared.paymentPublicId(), prepared.refundPublicId().toString(), prepared.amountMinor(), "PROCESSING");
        }
        processRefund(prepared.refundId());
        Refund r = refunds.findById(prepared.refundId()).orElseThrow();
        return new RefundResult(prepared.paymentPublicId(),
                r.getProviderRefundId() != null ? r.getProviderRefundId() : r.getPublicId().toString(),
                r.getAmountMinor(), r.getStatus().name());
    }

    PreparedRefund queueCapturedPaymentRefund(Long paymentId, String reason) {
        PreparedRefund prepared = tx.execute(status -> {
            Payment payment = payments.findById(paymentId).orElseThrow();
            return prepareRefund(payment.getPublicId(), reason, null, "SYSTEM", true);
        });
        if (prepared != null && !prepared.alreadyQueued()) processRefund(prepared.refundId());
        return prepared;
    }

    PreparedRefund queueCapturedPaymentRefundOnly(Long paymentId, String reason) {
        return tx.execute(status -> {
            Payment payment = payments.findById(paymentId).orElseThrow();
            return prepareRefund(payment.getPublicId(), reason, null, "SYSTEM", true);
        });
    }

    private PreparedRefund prepareRefund(UUID publicId, String reason, Long actorId, String role, boolean allowSystem) {
        String rr = reason == null ? "Event cancellation" : reason.trim();
        if (rr.length() > 500) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFUND_REASON", "Refund reason is too long");

        Payment p = payments.findByPublicId(publicId)
                .flatMap(x -> payments.findByIdForUpdate(x.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
        if (p.getStatus() != Enums.PaymentStatus.CAPTURED
                && p.getStatus() != Enums.PaymentStatus.COMPLETED
                && p.getStatus() != Enums.PaymentStatus.REFUND_PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_NOT_ALLOWED", "Payment is not captured");
        }

        Order o = orders.findById(p.getOrderId()).orElseThrow();
        Event event = events.findById(o.getEventId()).orElseThrow();
        if (!allowSystem && !isFinancialRefundApprover(role, event.getOrganizerId(), actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized to refund this payment");
        }

        List<Refund> existing = refunds.findAllByPaymentIdOrderByCreatedAtAscIdAsc(p.getId());
        long completedMinor = existing.stream()
                .filter(r -> r.getStatus() == Enums.RefundStatus.COMPLETED)
                .mapToLong(Refund::getAmountMinor)
                .sum();
        if (completedMinor >= p.getAmountMinor()) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_EXISTS", "The payment has already been fully refunded");
        }

        Refund activeLocal = existing.stream()
                .filter(r -> r.getProviderRefundId() == null)
                .filter(r -> r.getStatus() == Enums.RefundStatus.PROCESSING)
                .reduce((a, b) -> b)
                .orElse(null);
        if (activeLocal != null) {
            return new PreparedRefund(p.getPublicId(), activeLocal.getId(), activeLocal.getPublicId(), activeLocal.getAmountMinor(), true);
        }

        Refund activeExternal = existing.stream()
                .filter(r -> r.getProviderRefundId() != null)
                .filter(r -> r.getStatus() == Enums.RefundStatus.PROCESSING)
                .findFirst()
                .orElse(null);
        if (activeExternal != null) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_IN_PROGRESS", "A provider refund is still processing for this payment");
        }

        long remainingMinor = p.getAmountMinor() - completedMinor;
        if (remainingMinor <= 0) throw new ApiException(HttpStatus.CONFLICT, "REFUND_EXISTS", "The payment has already been fully refunded");
        if (tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream().anyMatch(t -> t.getStatus() == Enums.TicketStatus.CHECKED_IN)) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_NOT_ALLOWED", "A ticket has already been checked in");
        }

        // Keep failed refund attempts as immutable audit history. A new explicit retry gets a
        // fresh local refund ID, provider receipt and idempotency key, preventing a stale provider
        // idempotency result from suppressing a legitimate retry.
        Refund r = new Refund();
        r.setPaymentId(p.getId());
        r.setAmountMinor(remainingMinor);
        r.setReason(rr);
        r.setStatus(Enums.RefundStatus.PROCESSING);
        r.setAttemptCount(0);
        r.setLastError(null);
        if (r.getId() == null) refunds.saveAndFlush(r);

        paymentStateMachine.transition(p, Enums.PaymentStatus.REFUND_PENDING);
        o.setStatus(Enums.OrderStatus.CANCELLED);
        tickets.findByOrderIdOrderByTicketNumberAsc(o.getId())
                .forEach(t -> { if (t.getStatus() != Enums.TicketStatus.CHECKED_IN) t.setStatus(Enums.TicketStatus.CANCELLED); });
        reservationService.releaseOrder(o.getId());
        audit.log(actorId, "REFUND_QUEUED", "PAYMENT", p.getPublicId().toString(), null);
        return new PreparedRefund(p.getPublicId(), r.getId(), r.getPublicId(), r.getAmountMinor(), false);
    }

    public void reconcileProviderRefund(Long paymentId, String providerRefundId, String providerStatus, long amountMinor) {
        reconcileProviderRefund(paymentId, providerRefundId, providerStatus, amountMinor, null);
    }

    public void reconcileProviderRefund(Long paymentId, String providerRefundId, String providerStatus, long amountMinor, String providerReceipt) {
        if (amountMinor <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_AMOUNT_INVALID", "Provider refund amount must be positive");
        if (providerRefundId == null || providerRefundId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_PROVIDER_ID_REQUIRED", "Provider refund identifier is required");
        }
        var lock = locks.tryAcquire("refund-reconcile:" + paymentId, java.time.Duration.ofSeconds(60));
        if (!lock.acquired()) throw new ApiException(HttpStatus.CONFLICT, "REFUND_RECONCILIATION_BUSY", "Refund reconciliation is already in progress");
        try {
            try {
                tx.executeWithoutResult(status -> reconcileProviderRefundTx(paymentId, providerRefundId.trim(), providerStatus, amountMinor, providerReceipt));
            } catch (DataIntegrityViolationException ex) {
                // Concurrent delivery of the same provider refund can race the unique provider_refund_id key.
                Refund existing = refunds.findByProviderRefundId(providerRefundId.trim()).orElse(null);
                if (existing == null || !Objects.equals(existing.getPaymentId(), paymentId)) throw ex;
                if (existing.getAmountMinor() != amountMinor) {
                    throw new ApiException(HttpStatus.CONFLICT, "REFUND_EVENT_MISMATCH", "Provider refund amount changed for an existing refund");
                }
            }
        } finally {
            lock.close();
        }
    }

    private void reconcileProviderRefundTx(Long paymentId, String providerRefundId, String providerStatus, long amountMinor, String providerReceipt) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_FOUND", "Payment for provider refund was not found"));
        if (amountMinor > payment.getAmountMinor()) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_EVENT_MISMATCH", "Provider refund exceeds the local payment amount");
        }

        List<Refund> existing = refunds.findAllByPaymentIdOrderByCreatedAtAscIdAsc(paymentId);
        Refund r = refunds.findByProviderRefundId(providerRefundId).orElse(null);
        if (r != null && !Objects.equals(r.getPaymentId(), paymentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_PROVIDER_ID_REUSED", "Provider refund is already linked to another payment");
        }

        // A provider receipt identifies a refund request, but it may legitimately be reused by
        // multiple provider refund records when a provider performs partial/multiple refunds.
        // Reuse a local request only when the amount also matches and it is not already linked
        // to another provider refund; otherwise materialize a separate external refund record.
        if (r == null && providerReceipt != null && !providerReceipt.isBlank()) {
            r = existing.stream()
                    .filter(x -> providerReceipt.trim().equals(x.getProviderReceipt()))
                    .filter(x -> x.getProviderRefundId() == null)
                    .filter(x -> x.getAmountMinor() == amountMinor)
                    .filter(x -> x.getStatus() == Enums.RefundStatus.PROCESSING)
                    .reduce((a, b) -> b)
                    .orElse(null);
        }
        if (r == null) {
            r = new Refund();
            r.setPaymentId(paymentId);
            r.setAmountMinor(amountMinor);
            r.setReason("External provider refund");
            r.setAttemptCount(0);
        }
        if (r.getProviderRefundId() != null && !Objects.equals(r.getProviderRefundId(), providerRefundId)) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_PROVIDER_ID_MISMATCH", "A different provider refund is already linked to this refund");
        }
        if (r.getId() != null && r.getAmountMinor() != amountMinor) {
            // Never overwrite a local full-refund request with a smaller external partial refund.
            // The partial provider refund gets its own Refund row so the ledger remains accurate.
            Refund replacement = new Refund();
            replacement.setPaymentId(paymentId);
            replacement.setAmountMinor(amountMinor);
            replacement.setReason("External provider refund");
            replacement.setAttemptCount(0);
            r = replacement;
        }

        Long currentRefundId = r.getId();
        boolean done = isCompletedStatus(providerStatus);
        boolean failed = isFailedStatus(providerStatus);

        long otherCompleted = existing.stream()
                .filter(x -> x.getStatus() == Enums.RefundStatus.COMPLETED)
                .filter(x -> currentRefundId == null || !Objects.equals(x.getId(), currentRefundId))
                .mapToLong(Refund::getAmountMinor)
                .sum();
        if (done && otherCompleted + amountMinor > payment.getAmountMinor()) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_TOTAL_EXCEEDS_PAYMENT", "Provider refunds exceed the local payment amount");
        }

        r.setProviderRefundId(providerRefundId);
        if (providerReceipt != null && !providerReceipt.isBlank()) r.setProviderReceipt(providerReceipt.trim());
        r.setProviderStatus(providerStatus == null ? "" : providerStatus);
        r.setLastAttemptAt(Instant.now());
        r.setAttemptCount(r.getAttemptCount() + 1);
        r.setStatus(done ? Enums.RefundStatus.COMPLETED : (failed ? Enums.RefundStatus.FAILED : Enums.RefundStatus.PROCESSING));
        if (r.getId() == null) refunds.saveAndFlush(r);

        long totalCompleted = existing.stream()
                .filter(x -> x.getStatus() == Enums.RefundStatus.COMPLETED)
                .filter(x -> currentRefundId == null || !Objects.equals(x.getId(), currentRefundId))
                .mapToLong(Refund::getAmountMinor)
                .sum() + (done ? amountMinor : 0);

        // A locally initiated refund request may have been satisfied by several external
        // provider refunds. Do not leave that request stuck in PROCESSING when no single
        // provider refund can represent its full amount. Its provider-linked ledger rows are
        // the source of truth in this case.
        if (done && totalCompleted < payment.getAmountMinor()) {
            existing.stream()
                    .filter(x -> x.getProviderRefundId() == null)
                    .filter(x -> x.getStatus() == Enums.RefundStatus.PROCESSING)
                    .forEach(x -> {
                        x.setStatus(Enums.RefundStatus.FAILED);
                        x.setProviderStatus("PARTIAL_PROVIDER_REFUND");
                        x.setLastError("A partial provider refund was reconciled. Automatic top-up is disabled; retry the remaining refundable balance explicitly.");
                        x.setLastAttemptAt(Instant.now());
                    });
        }

        if (done && totalCompleted >= payment.getAmountMinor()) {
            if (payment.getStatus() != Enums.PaymentStatus.REFUNDED) {
                if (payment.getStatus() != Enums.PaymentStatus.CAPTURED
                        && payment.getStatus() != Enums.PaymentStatus.COMPLETED
                        && payment.getStatus() != Enums.PaymentStatus.REFUND_PENDING) {
                    paymentStateMachine.transition(payment, Enums.PaymentStatus.CAPTURED);
                }
                paymentStateMachine.transition(payment, Enums.PaymentStatus.REFUNDED);
            }
            existing.stream()
                    .filter(x -> x.getProviderRefundId() == null)
                    .filter(x -> x.getStatus() == Enums.RefundStatus.PROCESSING)
                    .forEach(x -> {
                        x.setStatus(Enums.RefundStatus.FAILED);
                        x.setProviderStatus("FULFILLED_BY_EXTERNAL_REFUNDS");
                        x.setLastError("Payment was fully refunded through one or more provider refund transactions.");
                        x.setLastAttemptAt(Instant.now());
                    });
            orders.findById(payment.getOrderId()).ifPresent(o -> {
                o.setStatus(Enums.OrderStatus.CANCELLED);
                reservationService.releaseOrder(o.getId());
            });
            tickets.findByOrderIdOrderByTicketNumberAsc(payment.getOrderId())
                    .forEach(t -> { if (t.getStatus() != Enums.TicketStatus.CHECKED_IN) t.setStatus(Enums.TicketStatus.REFUNDED); });
            audit.log(null, "REFUND_COMPLETED", "PAYMENT", payment.getPublicId().toString(), null);
        } else if (done) {
            audit.log(null, "PARTIAL_PROVIDER_REFUND_RECONCILED", "PAYMENT", payment.getPublicId().toString(), null);
        } else if (failed) {
            audit.log(null, "PROVIDER_REFUND_FAILED", "PAYMENT", payment.getPublicId().toString(), null);
        }
    }

    public void processRefund(Long refundId) {
        Refund pending = refunds.findById(refundId).orElse(null);
        if (pending == null || pending.getStatus() == Enums.RefundStatus.COMPLETED) return;
        Payment payment = payments.findById(pending.getPaymentId()).orElse(null);
        if (payment == null || payment.getProviderOrderId() == null) return;
        var lock = locks.tryAcquire("refund:" + payment.getId(), java.time.Duration.ofSeconds(90));
        if (!lock.acquired()) return;
        try { finalizeFromProvider(pending.getId(), payment); } finally { lock.close(); }
    }

    private void finalizeFromProvider(Long refundId, Payment payment) {
        Refund pending = refunds.findById(refundId).orElse(null);
        if (pending == null || pending.getStatus() == Enums.RefundStatus.COMPLETED) return;
        try {
            PaymentGatewayProvider provider = gateways.forPayment(payment);
            var providerPayments = provider.fetchPaymentsForOrder(payment.getProviderOrderId());
            var providerPayment = selectCapturedProviderPayment(payment, providerPayments);
            if (providerPayment == null || providerPayment.id() == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_PENDING", "Provider payment is not yet captured and available for refund");
            }

            String receipt = pending.getProviderReceipt();
            String clean = pending.getPublicId().toString().replace("-", "");
            String desired = "LKRF-" + clean.substring(0, 16);
            if (!desired.equals(receipt)) {
                receipt = desired;
                final String rr = receipt;
                tx.executeWithoutResult(s -> refunds.findById(refundId).ifPresent(x -> x.setProviderReceipt(rr)));
            }
            String idempotency = "LKREF-" + clean.substring(0, 24);
            final String refundReceipt = receipt;
            var providerRefunds = payment.getProvider() == Enums.PaymentProvider.CASHFREE
                    ? provider.fetchRefundsForOrder(payment.getProviderOrderId())
                    : provider.fetchRefundsForPayment(providerPayment.id());

            // Reconcile every known provider refund for this payment, including refunds created
            // manually in the provider dashboard. Never issue another refund while a provider
            // refund is still processing. This makes provider state authoritative and prevents
            // accidental over-refunds when a previous request succeeded but its response was lost.
            for (var providerRefund : providerRefunds) {
                if (providerRefund.id() == null || providerRefund.id().isBlank() || providerRefund.amount() <= 0) continue;
                reconcileProviderRefund(payment.getId(), providerRefund.id(), providerRefund.status(), providerRefund.amount(), providerRefund.receipt());
            }
            boolean hasProcessingProviderRefund = providerRefunds.stream()
                    .anyMatch(x -> x.id() != null && x.amount() > 0 && !isCompletedStatus(x.status()) && !isFailedStatus(x.status()));
            if (hasProcessingProviderRefund) {
                tx.executeWithoutResult(status -> refunds.findById(refundId).ifPresent(r -> {
                    r.setProviderStatus("REFUND_PROCESSING");
                    r.setLastAttemptAt(Instant.now());
                    r.setStatus(Enums.RefundStatus.PROCESSING);
                }));
                return;
            }

            long completedProviderTotal = providerRefunds.stream()
                    .filter(x -> x.id() != null && x.amount() > 0 && isCompletedStatus(x.status()))
                    .mapToLong(PaymentGatewayProvider.ProviderRefund::amount)
                    .sum();
            long remainingProviderAmount = payment.getAmountMinor() - completedProviderTotal;
            if (remainingProviderAmount <= 0) return;

            // Only issue the amount represented by this local refund record. If an external/partial
            // provider refund was discovered, reconciliation marks this full-refund request as
            // failed/manual when its amount no longer matches the provider balance. An explicit
            // retry then creates a fresh refund record for the remaining refundable amount.
            final long effectiveRefundAmount = pending.getAmountMinor();
            if (effectiveRefundAmount <= 0 || effectiveRefundAmount > remainingProviderAmount) {
                // The provider already refunded part of the payment and the current local refund
                // request represents more than the remaining balance. Its row has been marked
                // failed by reconciliation so an explicit retry can create a fresh refund record
                // with a fresh receipt/idempotency key and the correct remaining amount.
                return;
            }

            var result = provider.refund(providerPayment.id(), payment.getProviderOrderId(), effectiveRefundAmount, pending.getReason(), refundReceipt, idempotency);
            if (result == null || result.id() == null) {
                tx.executeWithoutResult(status -> refunds.findById(refundId).ifPresent(r -> {
                    r.setProviderStatus("PENDING");
                    r.setStatus(Enums.RefundStatus.PROCESSING);
                    r.setLastAttemptAt(Instant.now());
                    r.setAttemptCount(r.getAttemptCount() + 1);
                }));
            } else {
                reconcileProviderRefund(payment.getId(), result.id(), result.status(), result.amount(), result.receipt());
            }
        } catch (Exception ex) {
            tx.executeWithoutResult(s -> refunds.findById(refundId).ifPresent(r -> {
                r.setLastError(safeError(ex));
                r.setLastAttemptAt(Instant.now());
                r.setAttemptCount(r.getAttemptCount() + 1);
                boolean nonRetryable = ex instanceof ApiException api && (
                        "REFUND_PROVIDER_ID_REUSED".equals(api.code())
                                || "REFUND_PROVIDER_ID_MISMATCH".equals(api.code())
                                || "REFUND_EVENT_MISMATCH".equals(api.code())
                                || "REFUND_TOTAL_EXCEEDS_PAYMENT".equals(api.code()));
                r.setStatus(nonRetryable ? Enums.RefundStatus.FAILED : Enums.RefundStatus.PROCESSING);
            }));
        }
    }

    private PaymentGatewayProvider.ProviderPayment selectCapturedProviderPayment(Payment payment, List<PaymentGatewayProvider.ProviderPayment> candidates) {
        return gatewaysSelection(payment, candidates);
    }

    private PaymentGatewayProvider.ProviderPayment gatewaysSelection(Payment payment, List<PaymentGatewayProvider.ProviderPayment> candidates) {
        List<PaymentGatewayProvider.ProviderPayment> matches = candidates.stream()
                .filter(x -> x.amount() == payment.getAmountMinor() && payment.getCurrency().equalsIgnoreCase(x.currency()))
                .filter(x -> "captured".equalsIgnoreCase(x.status()) || "refunded".equalsIgnoreCase(x.status()))
                .toList();
        if (payment.getProviderPaymentId() != null && !payment.getProviderPaymentId().isBlank()) {
            return matches.stream().filter(x -> payment.getProviderPaymentId().equals(x.id())).findFirst().orElse(null);
        }
        if (matches.size() == 1) return matches.get(0);
        if (matches.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MULTIPLE_CAPTURED_PAYMENTS", "Multiple provider payments were found for this order; reconcile using the provider transaction ID");
        }
        return null;
    }

    private boolean isCompletedStatus(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("processed")
                || normalized.equals("completed")
                || normalized.equals("success")
                || normalized.equals("successful")
                || normalized.equals("refunded")
                || normalized.endsWith("_processed")
                || normalized.endsWith("_completed")
                || normalized.endsWith("_success");
    }

    private boolean isFailedStatus(String status) {
        if (status == null) return false;
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("failed")
                || normalized.equals("failure")
                || normalized.equals("cancelled")
                || normalized.equals("canceled")
                || normalized.equals("rejected")
                || normalized.endsWith("_failed")
                || normalized.endsWith("_failure")
                || normalized.endsWith("_cancelled")
                || normalized.endsWith("_canceled")
                || normalized.endsWith("_rejected");
    }

    private String safeError(Throwable ex) {
        String value = ex.getMessage();
        if (value == null || value.isBlank()) value = ex.getClass().getSimpleName();
        value = value.replaceAll("[\\r\\n\\t]", " ");
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private boolean isFinancialRefundApprover(String role, Long organizerId, Long actorId) {
        if ("ADMIN".equalsIgnoreCase(role) || "FINANCE".equalsIgnoreCase(role)) return true;
        if (!"ORGANIZER".equalsIgnoreCase(role) || actorId == null) return false;
        return members.findByOrganizerIdAndUserId(organizerId, actorId)
                .map(m -> "OWNER".equalsIgnoreCase(m.getRole()) || "FINANCE".equalsIgnoreCase(m.getRole()))
                .orElse(false);
    }

    public record PreparedRefund(UUID paymentPublicId, Long refundId, UUID refundPublicId, long amountMinor, boolean alreadyQueued) {}
    public record RefundResult(UUID paymentId, String refundId, long amountMinor, String status) {}
}
