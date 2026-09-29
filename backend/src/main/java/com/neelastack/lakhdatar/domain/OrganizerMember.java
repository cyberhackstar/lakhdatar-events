package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant;
@Entity @Table(name="organizer_members", uniqueConstraints=@UniqueConstraint(name="uk_organizer_member", columnNames={"organizer_id","user_id"})) @Getter @Setter
public class OrganizerMember {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="organizer_id",nullable=false) private Long organizerId;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(nullable=false,length=32) private String role;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
