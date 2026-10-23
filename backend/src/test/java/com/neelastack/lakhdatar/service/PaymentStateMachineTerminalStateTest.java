package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentStateMachineTerminalStateTest {
    @Test void authorizedPaymentMayBeCancelledAfterProviderConfirmsOrderExpiry() {
        Payment payment = new Payment();
        payment.setStatus(Enums.PaymentStatus.AUTHORIZED);
        new PaymentStateMachine().transition(payment, Enums.PaymentStatus.CANCELLED);
        assertEquals(Enums.PaymentStatus.CANCELLED, payment.getStatus());
    }
}
