package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="tickets") @Getter @Setter
public class Ticket {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="ticket_number",nullable=false,unique=true,length=40) private String ticketNumber;
 @Column(name="order_id",nullable=false) private Long orderId;
 @Column(name="order_item_id",nullable=false) private Long orderItemId;
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(name="ticket_type_id",nullable=false) private Long ticketTypeId;
 @Column(name="attendee_name") private String attendeeName;
 @Column(name="qr_credential_hash",nullable=false,unique=true,length=128) private String qrCredentialHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.TicketStatus status=Enums.TicketStatus.ISSUED;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.TicketSource source=Enums.TicketSource.ONLINE_PAYMENT;
 @Column(name="issued_by_user_id") private Long issuedByUserId;
 @Column(name="checked_in_at") private Instant checkedInAt;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 @Version @Column(nullable=false) private Long version; // null until first persist so Spring Data uses persist(), not merge()
}
