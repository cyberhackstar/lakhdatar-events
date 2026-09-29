package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="organizers") @Getter @Setter
public class Organizer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(nullable=false,length=255) private String name;
 @Column(nullable=false,unique=true,length=255) private String slug;
 private String website;
 @Column(name="contact_email") private String contactEmail;
 @Column(name="contact_phone") private String contactPhone;

 @Column(name="logo_url",length=500) private String logoUrl;
 @Column(columnDefinition="text") private String description;
 @Column(length=500) private String address;
 @Column(name="support_hours",length=255) private String supportHours;
 @Column(name="instagram_url",length=500) private String instagramUrl;
 @Column(name="facebook_url",length=500) private String facebookUrl;
 @Column(columnDefinition="text") private String terms;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
