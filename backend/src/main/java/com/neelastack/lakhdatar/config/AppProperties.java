package com.neelastack.lakhdatar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        Razorpay razorpay,
        Cashfree cashfree,
        Payment payment,
        InitialAdmin initialAdmin,
        Cloudinary cloudinary,
        String publicBaseUrl
) {
    public record Jwt(String secret, Duration accessToken, Duration refreshToken, String issuer, String audience) {
        public Jwt(String secret, Duration accessToken, Duration refreshToken) {
            this(secret, accessToken, refreshToken, "neelastack-events", "neelastack-events-web");
        }
    }
    public record Security(String ticketViewSecret, boolean refreshCookieSecure, Duration ticketViewTtl, String ticketViewPreviousSecret) {
        @ConstructorBinding
        public Security(String ticketViewSecret, boolean refreshCookieSecure, Duration ticketViewTtl, String ticketViewPreviousSecret) {
            this.ticketViewSecret = ticketViewSecret;
            this.refreshCookieSecure = refreshCookieSecure;
            this.ticketViewTtl = ticketViewTtl;
            this.ticketViewPreviousSecret = ticketViewPreviousSecret;
        }
        public Security(String ticketViewSecret, boolean refreshCookieSecure, Duration ticketViewTtl){this(ticketViewSecret,refreshCookieSecure,ticketViewTtl,"");}
    }
    public record Qr(String signingSecret, int imageSize, String previousSigningSecret) {
        @ConstructorBinding
        public Qr(String signingSecret, int imageSize, String previousSigningSecret) {
            this.signingSecret = signingSecret;
            this.imageSize = imageSize;
            this.previousSigningSecret = previousSigningSecret;
        }
        public Qr(String signingSecret, int imageSize){this(signingSecret,imageSize,"");}
    }
    public record Reservation(Duration hold, Duration sweep) {}
    public record Branding(String neelastackName, String neelastackPublicUrl, String neelastackLogoUrl,
                           String defaultOrganizerLogoUrl, boolean promoEnabled, String promoTitle,
                           String promoDescription, String promoCta) {}
    public record Checkout(int maxTicketsPerOrder, Duration sessionTtl) {
        @ConstructorBinding
        public Checkout(int maxTicketsPerOrder, Duration sessionTtl) {
            this.maxTicketsPerOrder = maxTicketsPerOrder;
            this.sessionTtl = sessionTtl;
        }
        public Checkout(int maxTicketsPerOrder){this(maxTicketsPerOrder,Duration.ofMinutes(20));}
    }
    public record Bootstrap(boolean enabled, String adminEmail, String adminPassword, String adminName,
                            String organizerName, String organizerSlug, String eventSlug) {}
    public record Cors(String allowedOrigins) {}
    public record RateLimit(int loginPerWindow, int publicCheckoutPerWindow, int scanPerMinute, int windowSeconds, boolean failClosedOnRedisError) {
        @ConstructorBinding
        public RateLimit(int loginPerWindow, int publicCheckoutPerWindow, int scanPerMinute, int windowSeconds, boolean failClosedOnRedisError) {
            this.loginPerWindow = loginPerWindow;
            this.publicCheckoutPerWindow = publicCheckoutPerWindow;
            this.scanPerMinute = scanPerMinute;
            this.windowSeconds = windowSeconds;
            this.failClosedOnRedisError = failClosedOnRedisError;
        }
        public RateLimit(int loginPerWindow,int publicCheckoutPerWindow,int scanPerMinute,int windowSeconds){this(loginPerWindow,publicCheckoutPerWindow,scanPerMinute,windowSeconds,true);}
    }
    public record Razorpay(String keyId, String keySecret, String webhookSecret, String baseUrl,
                           long reconciliationAgeMs, long reconciliationSweepMs, long httpConnectTimeoutMs, long httpReadTimeoutMs) {}
    public record Cashfree(String appId, String secretKey, String baseUrl, String apiVersion,
                           long reconciliationAgeMs, long reconciliationSweepMs, long httpConnectTimeoutMs, long httpReadTimeoutMs, long webhookToleranceMs) {}
    public record Payment(long reconciliationAgeMs, long reconciliationSweepMs) {}
    public record InitialAdmin(boolean enabled, String setupToken) {}
    public record Cloudinary(String cloudName, String apiKey, String apiSecret, String folder, long maxBytes) {}
}
