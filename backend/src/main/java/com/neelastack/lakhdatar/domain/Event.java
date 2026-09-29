package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="events") @Getter @Setter
public class Event {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(nullable=false,unique=true) private String slug;
 @Column(name="organizer_id",nullable=false) private Long organizerId;
 @Column(name="venue_id") private Long venueId;
 @Column(name="brand_config_id") private Long brandConfigId;
 @Column(nullable=false) private String name;
 @Column(columnDefinition="text") private String description;
 @Column(name="starts_at",nullable=false) private Instant startsAt;
 @Column(name="ends_at") private Instant endsAt;
 private Integer capacity;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.EventStatus status=Enums.EventStatus.DRAFT;
 @Column(nullable=false,length=8) private String currency="INR";

 @Column(name="short_description",length=500) private String shortDescription;
 @Column(nullable=false,length=60) private String category="General";
 @Column(nullable=false,length=64) private String timezone="Asia/Kolkata";
 @Column(name="cover_image_url",length=500) private String coverImageUrl;
 @Column(name="gallery_urls",columnDefinition="text") private String galleryUrls;
 @Column(columnDefinition="text") private String highlights;
 @Column(name="booking_starts_at") private Instant bookingStartsAt;
 @Column(name="booking_ends_at") private Instant bookingEndsAt;
 @Column(name="published_at") private Instant publishedAt;
 @Column(columnDefinition="text") private String terms;
 @Column(name="refund_policy",columnDefinition="text") private String refundPolicy;
 @Column(name="age_restriction",length=120) private String ageRestriction;
 @Column(nullable=false) private boolean featured=false;
 @Column(name="display_order",nullable=false) private int displayOrder=0;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 @Column(name="updated_at") private Instant updatedAt=Instant.now();
 @PreUpdate void touch(){updatedAt=Instant.now();}
}
