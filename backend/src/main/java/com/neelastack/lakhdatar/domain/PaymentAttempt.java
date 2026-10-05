package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable-ish provider attempt mapping. A single local payment can legitimately have
 * multiple provider transaction IDs (for example Cashfree retries or user-dropped attempts),
 * so the payment row's single provider_payment_id is intentionally not used as the only
 * recovery index.
 */
@Entity
@Table(name = "payment_attempts", indexes = {
        @Index(name = "idx_payment_attempts_payment_id", columnList = "payment_id"),
        @Index(name = "idx_payment_attempts_provider_order_id", columnList = "provider_order_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempts_provider_payment_id", columnNames = "provider_payment_id")
})
@Getter
@Setter
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 24)
    private Enums.PaymentProvider provider;

    @Column(name = "provider_payment_id", nullable = false, length = 255)
    private String providerPaymentId;

    @Column(name = "provider_order_id", length = 255)
    private String providerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Enums.PaymentStatus status = Enums.PaymentStatus.PENDING;

    @Column(name = "amount_minor", nullable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "INR";

    @Column(name = "provider_message", length = 500)
    private String providerMessage;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (firstSeenAt == null) firstSeenAt = Instant.now();
        if (lastSeenAt == null) lastSeenAt = firstSeenAt;
    }

    @PreUpdate
    void onUpdate() {
        lastSeenAt = Instant.now();
    }
}
