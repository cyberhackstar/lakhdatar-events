package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant;
@Entity @Table(name="event_staff",uniqueConstraints=@UniqueConstraint(name="uk_event_staff",columnNames={"event_id","user_id"})) @Getter @Setter
public class EventStaff {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(length=120) private String gate;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
