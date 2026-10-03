package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.BrandConfiguration;
import com.neelastack.lakhdatar.repository.BrandConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandService {
    private final BrandConfigurationRepository brands;
    private final AppProperties props;

    public record BrandView(
            String organizerLogoUrl, String organizerName, String brandingMode, String eventLogoUrl, String eventBannerUrl,
            String primaryBrandColor, String secondaryBrandColor, boolean technologyPartnerEnabled,
            String technologyPartnerName, String technologyPartnerLogoUrl, String technologyPartnerUrl,
            boolean promoEnabled, String promoTitle, String promoDescription, String promoCtaText, String promoCtaUrl) {}

    public BrandView view(Long id, String organizerName) {
        BrandConfiguration b = id == null ? null : brands.findById(id).orElse(null);
        if (b == null) return defaults(organizerName);
        return new BrandView(
                // Organizer identity comes from the organizer record / Cloudinary upload; there is no built-in organizer logo.
                blankToNull(b.getOrganizerLogoUrl()),
                value(b.getOrganizerName(), organizerName),
                normalizeMode(b.getBrandingMode()),
                b.getEventLogoUrl(), b.getEventBannerUrl(),
                value(b.getPrimaryBrandColor(), "#D9A441"), value(b.getSecondaryBrandColor(), "#7A1F3D"),
                b.isTechnologyPartnerEnabled(), value(b.getTechnologyPartnerName(), props.branding().neelastackName()),
                // The Neelastack mark is platform-controlled: always the bundled logo, never a value copied into old rows.
                props.branding().neelastackLogoUrl(),
                value(b.getTechnologyPartnerUrl(), props.branding().neelastackPublicUrl()),
                b.isPromoEnabled(), value(b.getPromoTitle(), props.branding().promoTitle()),
                value(b.getPromoDescription(), props.branding().promoDescription()),
                value(b.getPromoCtaText(), props.branding().promoCta()),
                value(b.getPromoCtaUrl(), props.branding().neelastackPublicUrl()));
    }

    private BrandView defaults(String organizerName) {
        return new BrandView(
                null, organizerName, "BOTH", null, null,
                "#D9A441", "#7A1F3D", props.branding().promoEnabled(),
                props.branding().neelastackName(), props.branding().neelastackLogoUrl(), props.branding().neelastackPublicUrl(),
                props.branding().promoEnabled(), props.branding().promoTitle(), props.branding().promoDescription(),
                props.branding().promoCta(), props.branding().neelastackPublicUrl());
    }

    private String blankToNull(String candidate) { return candidate == null || candidate.isBlank() ? null : candidate; }
    private String value(String candidate, String fallback) { return candidate == null || candidate.isBlank() ? fallback : candidate; }
    private String normalizeMode(String mode) { return "TEXT_ONLY".equals(mode) || "LOGO_ONLY".equals(mode) || "BOTH".equals(mode) ? mode : "BOTH"; }
}
