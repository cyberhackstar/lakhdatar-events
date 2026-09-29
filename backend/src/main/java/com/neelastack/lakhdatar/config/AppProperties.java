package com.neelastack.lakhdatar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Security security,
        Qr qr,
        Reservation reservation,
        Branding branding,
        Checkout checkout,
        Bootstrap bootstrap,
        Cors cors,
        RateLimit rateLimit,
        Razorpay razorpay
) {
    public record Jwt(String secret, Duration accessToken, Duration refreshToken) {}
    public record Security(String ticketViewSecret, boolean refreshCookieSecure, Duration ticketViewTtl) {}
    public record Qr(String signingSecret, int imageSize) {}
    public record Reservation(Duration hold, Duration sweep) {}
    public record Branding(String neelastackName, String neelastackPublicUrl, String neelastackLogoUrl,
                           String defaultOrganizerLogoUrl, boolean promoEnabled, String promoTitle,
                           String promoDescription, String promoCta) {}
    public record Checkout(int maxTicketsPerOrder) {}
    public record Bootstrap(boolean enabled, String adminEmail, String adminPassword, String adminName,
                            String organizerName, String organizerSlug, String eventSlug) {}
    public record Cors(String allowedOrigins) {}
    public record RateLimit(int loginPerWindow, int publicCheckoutPerWindow, int scanPerMinute, int windowSeconds) {}
    public record Razorpay(String keyId, String keySecret, String webhookSecret, String baseUrl,
                           long reconciliationAgeMs, long reconciliationSweepMs, long httpConnectTimeoutMs, long httpReadTimeoutMs) {}
}
