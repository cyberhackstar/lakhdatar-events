export interface BrandConfig {
  organizerLogoUrl: string;
  organizerName: string;
  brandingMode?: 'TEXT_ONLY' | 'LOGO_ONLY' | 'BOTH';
  eventLogoUrl?: string;
  eventBannerUrl?: string;
  primaryBrandColor?: string;
  secondaryBrandColor?: string;
  technologyPartnerEnabled: boolean;
  technologyPartnerName: string;
  technologyPartnerLogoUrl: string;
  technologyPartnerUrl: string;
  promoEnabled: boolean;
  promoTitle?: string;
  promoDescription?: string;
  promoCtaText?: string;
  promoCtaUrl?: string;
}
