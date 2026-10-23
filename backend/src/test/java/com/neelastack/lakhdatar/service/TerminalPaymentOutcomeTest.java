package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TerminalPaymentOutcomeTest {
    private static PaymentGatewayProvider.ProviderOrder order(String status) {
        return new PaymentGatewayProvider.ProviderOrder("provider-order", null, null,
                12500L, "INR", "LK-ABC123DEF456", status);
    }

    private static PaymentGatewayProvider.ProviderPayment attempt(String status, boolean captured, long refunded) {
        return new PaymentGatewayProvider.ProviderPayment("provider-payment", "provider-order",
                12500L, "INR", status, null, refunded, null, captured);
    }

    @Test void terminalOrderWithoutAttemptsRemainsUnresolved() {
        assertTrue(TerminalPaymentOutcome.classify(order("expired"), List.of()).isEmpty());
    }

    @Test void terminalOrderWithExplicitUserDroppedAttemptIsCancelled() {
        assertEquals(Optional.of(Enums.PaymentStatus.CANCELLED),
                TerminalPaymentOutcome.classify(order("expired"), List.of(attempt("user_dropped", false, 0))));
    }

    @Test void confirmedTerminalOrderWithOnlyFailedAttemptsIsFailure() {
        assertEquals(Optional.of(Enums.PaymentStatus.FAILED),
                TerminalPaymentOutcome.classify(order("expired"), List.of(attempt("failed", false, 0))));
    }

    @Test void activeOrUnknownOrderIsNeverTreatedAsNoCapture() {
        assertTrue(TerminalPaymentOutcome.classify(order("active"), List.of()).isEmpty());
        assertTrue(TerminalPaymentOutcome.classify(order("unknown"), List.of()).isEmpty());
    }

    @Test void pendingOrUnknownAttemptPreventsTerminalization() {
        assertTrue(TerminalPaymentOutcome.classify(order("expired"), List.of(attempt("pending", false, 0))).isEmpty());
        assertTrue(TerminalPaymentOutcome.classify(order("expired"), List.of(attempt("mystery_status", false, 0))).isEmpty());
    }

    @Test void capturedOrRefundedAttemptPreventsNoCaptureTerminalization() {
        assertTrue(TerminalPaymentOutcome.classify(order("expired"), List.of(attempt("success", true, 0))).isEmpty());
        assertTrue(TerminalPaymentOutcome.classify(order("expired"), List.of(attempt("failed", false, 100))).isEmpty());
    }

    @Test void missingProviderOrderAndPaymentListsFailClosed() {
        assertTrue(TerminalPaymentOutcome.classify(null, List.of()).isEmpty());
        assertTrue(TerminalPaymentOutcome.classify(order("expired"), null).isEmpty());
    }
}
