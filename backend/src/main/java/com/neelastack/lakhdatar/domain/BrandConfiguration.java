package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="brand_configurations") @Getter @Setter
public class BrandConfiguration {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(nullable=false,length=32) private String scope;
 @Column(name="organizer_id") private Long organizerId;
 @Column(name="organizer_logo_url") private String organizerLogoUrl;
 @Column(name="organizer_logo_public_id",length=255) private String organizerLogoPublicId;
 @Column(name="branding_mode",nullable=false,length=16) private String brandingMode="BOTH";
 @Column(name="organizer_name") private String organizerName;
 @Column(name="event_logo_url") private String eventLogoUrl;
 @Column(name="event_logo_public_id",length=255) private String eventLogoPublicId;
 @Column(name="event_banner_url") private String eventBannerUrl;
 @Column(name="event_banner_public_id",length=255) private String eventBannerPublicId;
 @Column(name="primary_brand_color") private String primaryBrandColor;
 @Column(name="secondary_brand_color") private String secondaryBrandColor;
 @Column(name="technology_partner_enabled",nullable=false) private boolean technologyPartnerEnabled=true;
 @Column(name="technology_partner_name") private String technologyPartnerName="Neelastack";
 @Column(name="technology_partner_logo_url") private String technologyPartnerLogoUrl;
 @Column(name="technology_partner_url") private String technologyPartnerUrl;
 @Column(name="promo_title") private String promoTitle;
 @Column(name="promo_description",columnDefinition="text") private String promoDescription;
 @Column(name="promo_cta_text") private String promoCtaText;
 @Column(name="promo_cta_url") private String promoCtaUrl;
 @Column(name="promo_enabled",nullable=false) private boolean promoEnabled=true;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 @Column(name="updated_at") private Instant updatedAt=Instant.now();
 @PreUpdate void touch(){updatedAt=Instant.now();}
}
