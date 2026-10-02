package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;

@Service
public class PaymentStateMachine {
    private static final Map<Enums.PaymentStatus, EnumSet<Enums.PaymentStatus>> ALLOWED = Map.of(
        Enums.PaymentStatus.CREATED, EnumSet.of(Enums.PaymentStatus.PENDING, Enums.PaymentStatus.PAYMENT_INITIATED, Enums.PaymentStatus.AUTHORIZED, Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.FAILED, Enums.PaymentStatus.CANCELLED),
        Enums.PaymentStatus.PENDING, EnumSet.of(Enums.PaymentStatus.PAYMENT_INITIATED, Enums.PaymentStatus.AUTHORIZED, Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.FAILED, Enums.PaymentStatus.CANCELLED),
        Enums.PaymentStatus.PAYMENT_INITIATED, EnumSet.of(Enums.PaymentStatus.PENDING, Enums.PaymentStatus.AUTHORIZED, Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.FAILED, Enums.PaymentStatus.CANCELLED),
        Enums.PaymentStatus.AUTHORIZED, EnumSet.of(Enums.PaymentStatus.PENDING, Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.FAILED, Enums.PaymentStatus.CANCELLED),
        Enums.PaymentStatus.CAPTURED, EnumSet.of(Enums.PaymentStatus.COMPLETED, Enums.PaymentStatus.REFUND_PENDING, Enums.PaymentStatus.REFUNDED),
        Enums.PaymentStatus.COMPLETED, EnumSet.of(Enums.PaymentStatus.REFUND_PENDING, Enums.PaymentStatus.REFUNDED),
        Enums.PaymentStatus.FAILED, EnumSet.of(Enums.PaymentStatus.PENDING, Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.REFUND_PENDING, Enums.PaymentStatus.CANCELLED),
        Enums.PaymentStatus.CANCELLED, EnumSet.of(Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.REFUND_PENDING, Enums.PaymentStatus.REFUNDED),
        Enums.PaymentStatus.REFUND_PENDING, EnumSet.of(Enums.PaymentStatus.REFUNDED),
        Enums.PaymentStatus.REFUNDED, EnumSet.noneOf(Enums.PaymentStatus.class)
    );

    public void transition(Payment payment, Enums.PaymentStatus target) {
        Enums.PaymentStatus current = payment.getStatus();
        if (current == target) return;
        if (!ALLOWED.getOrDefault(current, EnumSet.noneOf(Enums.PaymentStatus.class)).contains(target)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE_TRANSITION", "Payment cannot transition from " + current + " to " + target);
        }
        payment.setStatus(target);
    }
}
