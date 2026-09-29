package com.neelastack.lakhdatar.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ProductionConfigurationGuard {
    public ProductionConfigurationGuard(AppProperties props, Environment environment) {
        boolean production = environment.matchesProfiles("prod", "production") || "production".equalsIgnoreCase(environment.getProperty("APP_ENV"));
        if (!production) return;
        String dbPassword = environment.getProperty("spring.datasource.password");
        requireNonBlank("DB_PASSWORD", dbPassword);
        if ("change-me".equals(dbPassword) || dbPassword.length() < 12)
            throw new IllegalStateException("DB_PASSWORD must be a unique password of at least 12 characters in production");
        requireSecret("JWT_SECRET", props.jwt().secret());
        requireSecret("TICKET_VIEW_SECRET", props.security().ticketViewSecret());
        requireSecret("QR_SIGNING_SECRET", props.qr().signingSecret());
        requireSecret("RAZORPAY_KEY_SECRET", props.razorpay().keySecret());
        requireSecret("RAZORPAY_WEBHOOK_SECRET", props.razorpay().webhookSecret());
        requireNonBlank("RAZORPAY_KEY_ID", props.razorpay().keyId());
        requireHttps("NEELASTACK_PUBLIC_URL", props.branding().neelastackPublicUrl());
        requireHttps("RAZORPAY_BASE_URL", props.razorpay().baseUrl());
        requireHttpsOrigins("CORS_ALLOWED_ORIGINS", props.cors().allowedOrigins());
        if (!props.security().refreshCookieSecure()) throw new IllegalStateException("AUTH_COOKIE_SECURE must be true in production");
        if (props.bootstrap().enabled()) throw new IllegalStateException("BOOTSTRAP_ENABLED must be false in production");
    }

    private void requireSecret(String name, String value) {
        requireNonBlank(name, value);
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32 || value.startsWith("replace-with-") || value.startsWith("change-me"))
            throw new IllegalStateException(name + " must be a unique secret of at least 32 bytes in production");
    }
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
