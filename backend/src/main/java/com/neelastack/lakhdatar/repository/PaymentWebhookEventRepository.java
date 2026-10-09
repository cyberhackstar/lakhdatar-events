package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent,Long>{
    Optional<PaymentWebhookEvent> findByProviderEventId(String id);
    boolean existsByProviderEventId(String id);

    @Query(value="""
        SELECT *
        FROM payment_webhook_events
        WHERE provider = :provider
          AND processed = FALSE
          AND dead_letter = FALSE
          AND processing = FALSE
          AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        ORDER BY received_at ASC, id ASC
        LIMIT 100
        """, nativeQuery = true)
    List<PaymentWebhookEvent> findDueForProcessing(@Param("provider") String provider, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query(value="""
        UPDATE payment_webhook_events
           SET processing = TRUE,
               processing_started_at = :startedAt,
               attempt_count = attempt_count + 1,
               next_attempt_at = NULL,
               last_error = NULL
         WHERE provider_event_id = :providerEventId
           AND processed = FALSE
           AND dead_letter = FALSE
           AND processing = FALSE
           AND (next_attempt_at IS NULL OR next_attempt_at <= :startedAt)
           AND attempt_count < :maxAttempts
        """, nativeQuery = true)
    int claimForProcessing(@Param("providerEventId") String providerEventId,
                           @Param("startedAt") Instant startedAt,
                           @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying
    @Query(value="UPDATE payment_webhook_events SET processed=TRUE, processing=FALSE, processing_started_at=NULL, dead_letter=FALSE, processed_at=:processedAt, next_attempt_at=NULL, last_error=NULL WHERE provider_event_id=:providerEventId",nativeQuery=true)
    int markProcessed(@Param("providerEventId") String providerEventId, @Param("processedAt") Instant processedAt);

    @Transactional
    @Modifying
    @Query(value="UPDATE payment_webhook_events SET processing=FALSE, processing_started_at=NULL, last_error=:lastError, next_attempt_at=:nextAttemptAt, dead_letter=:deadLetter WHERE provider_event_id=:providerEventId AND processed=FALSE",nativeQuery=true)
    int markFailed(@Param("providerEventId") String providerEventId,
                   @Param("lastError") String lastError,
                   @Param("nextAttemptAt") Instant nextAttemptAt,
                   @Param("deadLetter") boolean deadLetter);

    @Transactional
    @Modifying
    @Query(value="""
        UPDATE payment_webhook_events
           SET processing = FALSE,
               last_error = 'stale-processing-reset',
               dead_letter = CASE WHEN attempt_count >= :maxAttempts THEN TRUE ELSE dead_letter END,
               next_attempt_at = CASE WHEN attempt_count >= :maxAttempts THEN NULL ELSE CAST(:retryAt AS timestamptz) END
         WHERE provider = :provider
           AND processed = FALSE
           AND processing = TRUE
           AND processing_started_at < :staleBefore
        """,nativeQuery=true)
    int resetStaleProcessing(@Param("provider") String provider,
                             @Param("staleBefore") Instant staleBefore,
                             @Param("retryAt") Instant retryAt,
                             @Param("maxAttempts") int maxAttempts);

    @Transactional
    @Modifying
    @Query(value="""
        DELETE FROM payment_webhook_events
         WHERE id IN (
             SELECT id
             FROM payment_webhook_events
             WHERE received_at < :before
               AND (processed = TRUE OR dead_letter = TRUE)
             ORDER BY received_at ASC, id ASC
             LIMIT :batchSize
         )
        """, nativeQuery = true)
    int deleteTerminalOlderThan(@Param("before") Instant before, @Param("batchSize") int batchSize);
}
