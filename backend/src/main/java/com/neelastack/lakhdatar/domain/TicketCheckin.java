package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant;
@Entity @Table(name="ticket_checkins") @Getter @Setter
public class TicketCheckin {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id") private Long eventId;
 @Column(name="ticket_id") private Long ticketId;
 @Column(name="staff_user_id") private Long staffUserId;
 @Column(length=120) private String gate;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.CheckInResult result;
 @Column(name="correlation_id",length=80) private String correlationId;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
