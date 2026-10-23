package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Conservative classifier for provider-authoritative no-capture outcomes. */
final class TerminalPaymentOutcome {
    private static final Set<String> TERMINAL_ORDER_STATES = Set.of(
            "expired", "cancelled", "canceled", "failed", "terminated", "closed");
    private static final Set<String> TERMINAL_ATTEMPT_STATES = Set.of(
            "failed", "cancelled", "canceled", "user_dropped", "user dropped");

    private TerminalPaymentOutcome() {}

    static Optional<Enums.PaymentStatus> classify(
            PaymentGatewayProvider.ProviderOrder order,
            List<PaymentGatewayProvider.ProviderPayment> attempts) {
        if (order == null || order.status() == null
                || !TERMINAL_ORDER_STATES.contains(order.status().trim().toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        if (attempts == null) return Optional.empty();
        for (PaymentGatewayProvider.ProviderPayment attempt : attempts) {
            if (attempt == null || attempt.captured() || attempt.amountRefunded() > 0) return Optional.empty();
            String status = attempt.status() == null ? "" : attempt.status().trim().toLowerCase(Locale.ROOT);
            // Unknown, pending or provider-specific statuses fail closed: do not infer no-capture.
            if (!TERMINAL_ATTEMPT_STATES.contains(status)) return Optional.empty();
        }
        // An empty attempt list proves only that no payment attempt is currently visible.
        // It does not prove a failure, and must not trigger local terminalization.
        if (attempts.isEmpty()) return Optional.empty();
        boolean anyFailure = attempts.stream().anyMatch(a -> {
            String state = a.status() == null ? "" : a.status().trim().toLowerCase(Locale.ROOT);
            return "failed".equals(state);
        });
        return Optional.of(anyFailure ? Enums.PaymentStatus.FAILED : Enums.PaymentStatus.CANCELLED);
    }
}
