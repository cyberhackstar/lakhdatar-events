package com.neelastack.lakhdatar.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ProductionConfigurationGuard {
    public ProductionConfigurationGuard(AppProperties props, Environment environment) {
        boolean production = environment.matchesProfiles("prod", "production") || "production".equalsIgnoreCase(environment.getProperty("APP_ENV"));
        if (!production) {
            // The strict checks below are opt-in (prod profile or APP_ENV=production). A deployment that
            // forgets both flags but is clearly public-facing (non-local HTTPS origins) must still never
            // boot with the publicly known placeholder secrets from application.yml.
            if (servesPublicOrigins(props.cors().allowedOrigins())) {
                rejectPlaceholder("DB_PASSWORD", environment.getProperty("spring.datasource.password"));
                rejectPlaceholder("JWT_SECRET", props.jwt().secret());
                rejectPlaceholder("TICKET_VIEW_SECRET", props.security().ticketViewSecret());
                rejectPlaceholder("QR_SIGNING_SECRET", props.qr().signingSecret());
            }
            return;
        }
        String dbPassword = environment.getProperty("spring.datasource.password");
        requireNonBlank("DB_PASSWORD", dbPassword);
        if ("change-me".equals(dbPassword) || dbPassword.length() < 12)
            throw new IllegalStateException("DB_PASSWORD must be a unique password of at least 12 characters in production");
        requireSecret("JWT_SECRET", props.jwt().secret());
        requireSecret("TICKET_VIEW_SECRET", props.security().ticketViewSecret());
        requireSecret("QR_SIGNING_SECRET", props.qr().signingSecret());
        if (present(props.security().ticketViewPreviousSecret())) requireSecret("TICKET_VIEW_SECRET_PREVIOUS", props.security().ticketViewPreviousSecret());
        if (present(props.qr().previousSigningSecret())) requireSecret("QR_SIGNING_SECRET_PREVIOUS", props.qr().previousSigningSecret());
        String redisPassword = environment.getProperty("REDIS_PASSWORD");
        if (redisPassword == null || redisPassword.isBlank() || redisPassword.startsWith("change-me") || redisPassword.length() < 32) throw new IllegalStateException("REDIS_PASSWORD must be a unique secret of at least 32 characters in production");
        boolean razorpayConfigured = present(props.razorpay().keyId()) || present(props.razorpay().keySecret()) || present(props.razorpay().webhookSecret());
        boolean cashfreeConfigured = present(props.cashfree().appId()) || present(props.cashfree().secretKey());
        if (!razorpayConfigured && !cashfreeConfigured) throw new IllegalStateException("At least one payment provider must be configured in production");
        if (razorpayConfigured) { requireProviderCredential("RAZORPAY_KEY_SECRET", props.razorpay().keySecret()); requireSecret("RAZORPAY_WEBHOOK_SECRET", props.razorpay().webhookSecret()); requireNonBlank("RAZORPAY_KEY_ID", props.razorpay().keyId()); requireHttps("RAZORPAY_BASE_URL", props.razorpay().baseUrl()); }
        if (cashfreeConfigured) { requireProviderCredential("CASHFREE_SECRET_KEY", props.cashfree().secretKey()); requireNonBlank("CASHFREE_APP_ID", props.cashfree().appId()); requireHttps("CASHFREE_BASE_URL", props.cashfree().baseUrl()); }
        requireHttps("NEELASTACK_PUBLIC_URL", props.branding().neelastackPublicUrl());
        requireHttpsOrigins("CORS_ALLOWED_ORIGINS", props.cors().allowedOrigins());
        if (!props.security().refreshCookieSecure()) throw new IllegalStateException("AUTH_COOKIE_SECURE must be true in production");
        if (props.checkout().sessionTtl().isNegative() || props.checkout().sessionTtl().isZero() || props.checkout().sessionTtl().compareTo(java.time.Duration.ofHours(2)) > 0) throw new IllegalStateException("CHECKOUT_SESSION_TTL must be between >0 and 2 hours in production");
        if (!props.rateLimit().failClosedOnRedisError()) throw new IllegalStateException("RATE_LIMIT_FAIL_CLOSED must be true in production");
        boolean mfaRequired = environment.getProperty("app.mfa.required-for-privileged", Boolean.class, false);
        if (!mfaRequired) throw new IllegalStateException("MFA_REQUIRED_FOR_PRIVILEGED must be true in production");
        String mfaKey = environment.getProperty("app.mfa.encryption-key", "");
        requireNonBlank("MFA_ENCRYPTION_KEY", mfaKey);
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(mfaKey.trim());
            if (decoded.length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            if (!mfaKey.matches("(?i)[0-9a-f]{64}")) throw new IllegalStateException("MFA_ENCRYPTION_KEY must be a base64 or 64-hex encoded 32-byte secret when privileged MFA is required");
        }
        if (props.bootstrap().enabled()) throw new IllegalStateException("BOOTSTRAP_ENABLED must be false in production");
        if (props.initialAdmin().enabled()) requireSecret("INITIAL_ADMIN_SETUP_TOKEN", props.initialAdmin().setupToken());
        boolean cloudinaryConfigured = present(props.cloudinary().cloudName()) || present(props.cloudinary().apiKey()) || present(props.cloudinary().apiSecret());
        if (cloudinaryConfigured) {
            requireNonBlank("CLOUDINARY_CLOUD_NAME", props.cloudinary().cloudName());
            requireNonBlank("CLOUDINARY_API_KEY", props.cloudinary().apiKey());
            requireProviderCredential("CLOUDINARY_API_SECRET", props.cloudinary().apiSecret());
            if (props.cloudinary().maxBytes() < 1024 || props.cloudinary().maxBytes() > 10_000_000) throw new IllegalStateException("CLOUDINARY_MAX_BYTES must be between 1KB and 10MB");
        }
    }

    private void rejectPlaceholder(String name, String value) {
        if (value != null && (value.startsWith("replace-with-") || value.startsWith("change-me")))
            throw new IllegalStateException(name + " is still a placeholder value on a public-facing deployment; set a unique secret (or enable the prod profile)");
    }

    private boolean servesPublicOrigins(String origins) {
        if (origins == null || origins.isBlank()) return false;
        for (String origin : origins.split(",")) {
            String value = origin.trim();
            if (value.isBlank()) continue;
            try {
                URI uri = URI.create(value);
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(uri.getScheme()) && host != null
                        && !host.equalsIgnoreCase("localhost") && !host.equals("127.0.0.1") && !host.equals("::1") && !host.equals("[::1]")) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private void requireSecret(String name, String value) {
        requireNonBlank(name, value);
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32 || value.startsWith("replace-with-") || value.startsWith("change-me"))
            throw new IllegalStateException(name + " must be a unique secret of at least 32 bytes in production");
    }

    /**
     * Provider-issued credentials must be validated according to the provider's
     * contract, not by an application-defined minimum length. Razorpay API
     * secrets are provider-issued values and may legitimately be shorter than
     * the 32-byte minimum used for application-owned cryptographic secrets.
     */
    private void requireProviderCredential(String name, String value) {
        requireNonBlank(name, value);
        if (value.startsWith("replace-with-") || value.startsWith("change-me"))
            throw new IllegalStateException(name + " must be a real provider credential in production");
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
    private void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required in production");
    }
    private void requireHttps(String name, String value) {
        requireNonBlank(name, value);
        try { if (!"https".equalsIgnoreCase(URI.create(value).getScheme())) throw new IllegalArgumentException(); }
        catch (Exception e) { throw new IllegalStateException(name + " must use HTTPS in production"); }
    }
    private void requireHttpsOrigins(String name, String origins) {
        requireNonBlank(name, origins);
        for (String origin : origins.split(",")) {
            String value = origin.trim();
            if (value.isBlank()) continue;
            try { if (!"https".equalsIgnoreCase(URI.create(value).getScheme())) throw new IllegalArgumentException(); }
            catch (Exception e) { throw new IllegalStateException(name + " must contain only HTTPS origins in production"); }
        }
    }
}
