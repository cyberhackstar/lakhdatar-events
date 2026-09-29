package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="ticket_reservations") @Getter @Setter
public class TicketReservation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="ticket_type_id",nullable=false) private Long ticketTypeId;
 @Column(name="order_id") private Long orderId;
 @Column(nullable=false) private Integer quantity;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.ReservationStatus status=Enums.ReservationStatus.HELD;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
