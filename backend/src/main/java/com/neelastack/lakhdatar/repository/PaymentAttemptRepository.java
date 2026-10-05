package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByProviderPaymentId(String providerPaymentId);
    Optional<PaymentAttempt> findByPaymentIdAndProviderPaymentId(Long paymentId, String providerPaymentId);

    /**
     * PostgreSQL-safe idempotent insert. A duplicate provider payment ID is a normal
     * concurrency race between webhook/reconciliation/browser paths and must not abort
     * the surrounding transaction.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO payment_attempts
            (payment_id, provider, provider_payment_id, provider_order_id, status,
             amount_minor, currency, provider_message, first_seen_at, last_seen_at)
        VALUES
            (:paymentId, :provider, :providerPaymentId, :providerOrderId, :status,
             :amountMinor, :currency, :providerMessage, now(), now())
        ON CONFLICT (provider_payment_id) DO NOTHING
        """, nativeQuery = true)
    int insertIgnoreDuplicate(
            @Param("paymentId") Long paymentId,
            @Param("provider") String provider,
            @Param("providerPaymentId") String providerPaymentId,
            @Param("providerOrderId") String providerOrderId,
            @Param("status") String status,
            @Param("amountMinor") Long amountMinor,
            @Param("currency") String currency,
            @Param("providerMessage") String providerMessage);
}
