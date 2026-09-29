package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Converts event cancellation into durable, retryable refund work without blocking on Razorpay. */
@Component
@RequiredArgsConstructor
public class EventCancellationRefundJob {
    private static final Logger log = LoggerFactory.getLogger(EventCancellationRefundJob.class);
    private final PaymentRepository payments;
    private final RefundService refunds;

    @Scheduled(fixedDelayString = "${app.refund.cancellation-sweep:30000}")
    public void sweep() {
        var statuses = List.of(Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.COMPLETED, Enums.PaymentStatus.REFUND_PENDING);
        var candidates = payments.findByCancelledEventAndStatusWithoutRefund(
                Enums.EventStatus.CANCELLED, statuses, PageRequest.of(0, 100));
        for (var payment : candidates) {
            try {
                refunds.queueCapturedPaymentRefundOnly(payment.getId(), "Event cancellation");
            } catch (Exception ex) {
                log.warn("Could not queue event-cancellation refund paymentId={}", payment.getId(), ex);
            }
        }
    }
}
