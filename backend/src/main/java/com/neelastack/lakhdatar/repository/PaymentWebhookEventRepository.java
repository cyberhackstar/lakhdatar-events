package com.neelastack.lakhdatar.repository;

import com.neelastack.lakhdatar.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent,Long>{
    Optional<PaymentWebhookEvent> findByProviderEventId(String id);
    boolean existsByProviderEventId(String id);
    @Transactional
    @Modifying
    @Query(value="UPDATE payment_webhook_events SET processing=TRUE, processing_started_at=:startedAt, attempt_count=attempt_count+1, last_error=NULL WHERE provider_event_id=:providerEventId AND processed=FALSE AND processing=FALSE",nativeQuery=true)
    int claimForProcessing(@Param("providerEventId") String providerEventId, @Param("startedAt") Instant startedAt);
    @Transactional
    @Modifying
    @Query(value="UPDATE payment_webhook_events SET processed=TRUE, processing=FALSE, processed_at=:processedAt, last_error=NULL WHERE provider_event_id=:providerEventId",nativeQuery=true)
    int markProcessed(@Param("providerEventId") String providerEventId, @Param("processedAt") Instant processedAt);
    @Transactional
    @Modifying
    @Query(value="UPDATE payment_webhook_events SET processing=FALSE, last_error=:lastError WHERE provider_event_id=:providerEventId AND processed=FALSE",nativeQuery=true)
    int markFailed(@Param("providerEventId") String providerEventId, @Param("lastError") String lastError);
    @Transactional
    @Modifying
    @Query(value="UPDATE payment_webhook_events SET processing=FALSE, last_error='stale-processing-reset' WHERE processed=FALSE AND processing=TRUE AND processing_started_at < :staleBefore",nativeQuery=true)
    int resetStaleProcessing(@Param("staleBefore") Instant staleBefore);
}
