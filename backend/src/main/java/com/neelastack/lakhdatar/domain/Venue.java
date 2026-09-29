package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="venues") @Getter @Setter
public class Venue {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="organizer_id",nullable=false) private Long organizerId;
 @Column(nullable=false,length=255) private String name;
 private String address;
 private String city;

 @Column(length=120) private String state;
 @Column(nullable=false,length=120) private String country="India";
 @Column(name="map_url",length=500) private String mapUrl;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
