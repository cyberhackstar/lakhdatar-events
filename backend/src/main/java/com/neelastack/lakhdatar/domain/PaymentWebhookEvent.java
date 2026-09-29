package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name="payment_webhook_events")
@Getter @Setter
public class PaymentWebhookEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="provider_event_id",nullable=false,unique=true,length=150) private String providerEventId;
 @Column(name="event_type",nullable=false,length=80) private String eventType;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private String payload;
 @Column(nullable=false) private boolean processed=false;
 @Column(nullable=false) private boolean processing=false;
 @Column(name="attempt_count",nullable=false) private int attemptCount=0;
 @Column(name="received_at",updatable=false) private Instant receivedAt=Instant.now();
 @Column(name="processing_started_at") private Instant processingStartedAt;
 @Column(name="processed_at") private Instant processedAt;
 @Column(name="last_error",length=500) private String lastError;
 @Column(name="payload_hash",length=64) private String payloadHash;
}
