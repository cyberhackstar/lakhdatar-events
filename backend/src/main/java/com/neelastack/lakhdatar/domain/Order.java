package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="orders") @Getter @Setter
public class Order {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="order_number",nullable=false,unique=true,length=40) private String orderNumber;
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(name="user_id") private Long userId;
 @Column(name="customer_name",nullable=false) private String customerName;
 @Column(name="customer_email",nullable=false) private String customerEmail;
 @Column(name="customer_phone") private String customerPhone;
 @Column(name="total_minor_units",nullable=false) private Long totalMinorUnits;
 @Column(nullable=false,length=8) private String currency="INR";
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.OrderStatus status=Enums.OrderStatus.CREATED;
 @Column(name="idempotency_key",nullable=false,unique=true,length=100) private String idempotencyKey;
 @Column(name="checkout_session_hash",length=64) private String checkoutSessionHash;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 @Column(name="updated_at") private Instant updatedAt=Instant.now();
 @PreUpdate void touch(){updatedAt=Instant.now();}
}
