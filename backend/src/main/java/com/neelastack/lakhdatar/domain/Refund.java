package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="refunds") @Getter @Setter
public class Refund {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="payment_id",nullable=false) private Long paymentId;
 @Column(name="amount_minor",nullable=false) private Long amountMinor;
 private String reason;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.RefundStatus status=Enums.RefundStatus.REQUESTED;
 /** @deprecated retained read-only for legacy schema compatibility; use providerRefundId. */
 @Deprecated @Column(name="razorpay_refund_id", insertable=false, updatable=false) private String razorpayRefundId;
 @Column(name="provider_refund_id",unique=true,length=100) private String providerRefundId;
 @Column(name="provider_receipt",length=80) private String providerReceipt;
 @Column(name="provider_status",length=32) private String providerStatus;
 @Column(name="attempt_count",nullable=false) private int attemptCount=0;
 @Column(name="last_error") private String lastError;
 @Column(name="last_attempt_at") private Instant lastAttemptAt;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
