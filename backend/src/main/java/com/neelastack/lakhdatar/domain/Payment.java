package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="payments") @Getter @Setter
public class Payment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="order_id",nullable=false,unique=true) private Long orderId;
 @Enumerated(EnumType.STRING) @Column(name="provider",nullable=false,length=24) private Enums.PaymentProvider provider=Enums.PaymentProvider.RAZORPAY;
 @Column(name="provider_order_id",unique=true) private String providerOrderId;
 @Column(name="provider_payment_id",unique=true) private String providerPaymentId;
 @Column(name="provider_signature") private String providerSignature;
 @Column(name="provider_public_key",length=255) private String providerPublicKey;
 @Column(name="provider_session_id",length=500) private String providerSessionId;
 @Column(name="razorpay_order_id",unique=true) private String razorpayOrderId;
 @Column(name="razorpay_payment_id",unique=true) private String razorpayPaymentId;
 @Column(name="razorpay_signature") private String razorpaySignature;
 @Column(name="amount_minor",nullable=false) private Long amountMinor;
 @Column(nullable=false,length=8) private String currency="INR";
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.PaymentStatus status=Enums.PaymentStatus.CREATED;
 @Column(name="failure_reason") private String failureReason;
 @Column(name="razorpay_order_state",nullable=false,length=24) private String razorpayOrderState="NOT_CREATED";
 @Column(name="razorpay_order_attempts",nullable=false) private int razorpayOrderAttempts=0;
 @Column(name="provider_last_error") private String providerLastError;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 @Column(name="updated_at") private Instant updatedAt=Instant.now();
 @PreUpdate void touch(){updatedAt=Instant.now();}
}
