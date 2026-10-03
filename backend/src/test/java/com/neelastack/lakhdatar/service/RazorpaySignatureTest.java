package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.config.AppProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class RazorpaySignatureTest {
    private static final String KEY_SECRET = "unit-test-razorpay-secret-32-bytes-long!";
    private static final String WEBHOOK_SECRET = "unit-test-webhook-secret-32-bytes!";

    private RazorpayService service() {
        AppProperties p = new AppProperties(
                new AppProperties.Jwt("12345678901234567890123456789012", Duration.ofMinutes(30), Duration.ofDays(14)),
                new AppProperties.Security("12345678901234567890123456789012", false, java.time.Duration.ofHours(24)),
                new AppProperties.Qr("12345678901234567890123456789012", 512),
                new AppProperties.Reservation(Duration.ofMinutes(12), Duration.ofSeconds(30)),
                new AppProperties.Branding("Neelastack", "https://neelastack.com", "/assets/neelastack-logo.svg", "/assets/lakhdatar-logo.svg", true, "Powered by Neelastack", "Build modern digital experiences.", "Explore Neelastack"),
                new AppProperties.Checkout(20),
                new AppProperties.Bootstrap(false, "", "", "", "Lakhdatar Events", "lakhdatar-events", "dandiya-night-2026"),
                new AppProperties.Cors("http://localhost:4200"),
                new AppProperties.RateLimit(10, 20, 240, 60),
                new AppProperties.Razorpay("rzp_test", KEY_SECRET, WEBHOOK_SECRET, "https://example.invalid/v1", 120000, 30000, 3000, 8000),
                new AppProperties.Cashfree("", "", "https://example.invalid/pg", "2025-01-01", 120000, 30000, 3000, 8000, 300000),
                new AppProperties.Payment(120000, 30000),
                new AppProperties.InitialAdmin(false, ""),
                new AppProperties.Cloudinary("", "", "", "neelastack-events", 5242880),
                "https://events.example.com"
        );
        return new RazorpayService(p);
    }

    @Test
    void acceptsValidPaymentSignatureAndRejectsTampering() throws Exception {
        RazorpayService s = service();
        String order = "order_test_123";
        String payment = "pay_test_123";
        String valid = hmac(order + "|" + payment, KEY_SECRET);
        assertTrue(s.verifyPaymentSignature(order, payment, valid));
        assertFalse(s.verifyPaymentSignature(order, payment, valid.substring(0, valid.length() - 1) + "0"));
        assertFalse(s.verifyPaymentSignature(order, "pay_other", valid));
    }

    @Test
    void validatesWebhookSignatureWithConstantTimeComparison() throws Exception {
        RazorpayService s = service();
        String body = "{\"event\":\"payment.captured\"}";
        String valid = hmac(body, WEBHOOK_SECRET);
        assertTrue(s.verifyWebhookSignature(body, valid));
        assertFalse(s.verifyWebhookSignature(body + " ", valid));
    }

    @Test
    void cashfreeWebhookSignatureUsesRawBodyAndTimestampAndPaymentSignatureFailsClosed() throws Exception {
        AppProperties p = new AppProperties(
                new AppProperties.Jwt("12345678901234567890123456789012", Duration.ofMinutes(30), Duration.ofDays(14)),
                new AppProperties.Security("12345678901234567890123456789012", false, Duration.ofHours(24)),
                new AppProperties.Qr("12345678901234567890123456789012", 512),
                new AppProperties.Reservation(Duration.ofMinutes(12), Duration.ofSeconds(30)),
                new AppProperties.Branding("Neelastack", "https://neelastack.com", "/assets/neelastack-logo.svg", "/assets/lakhdatar-logo.svg", true, "Powered by Neelastack", "Build modern digital experiences.", "Explore Neelastack"),
                new AppProperties.Checkout(20),
                new AppProperties.Bootstrap(false, "", "", "", "Lakhdatar Events", "lakhdatar-events", "dandiya-night-2026"),
                new AppProperties.Cors("http://localhost:4200"),
                new AppProperties.RateLimit(10, 20, 240, 60),
                new AppProperties.Razorpay("rzp_test", KEY_SECRET, WEBHOOK_SECRET, "https://example.invalid/v1", 120000, 30000, 3000, 8000),
                new AppProperties.Cashfree("cf_app", "cf_secret", "https://example.invalid/pg", "2025-01-01", 120000, 30000, 3000, 8000, 300000),
                new AppProperties.Payment(120000, 30000),
                new AppProperties.InitialAdmin(false, ""),
                new AppProperties.Cloudinary("", "", "", "neelastack-events", 5242880),
                "https://events.example.com"
        );
        CashfreeGatewayProvider cashfree = new CashfreeGatewayProvider(p, new ObjectMapper());
        String timestamp = "" + System.currentTimeMillis();
        String raw = "{\"type\":\"PAYMENT_SUCCESS_WEBHOOK\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("cf_secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal((timestamp + raw).getBytes(StandardCharsets.UTF_8)));
        assertTrue(cashfree.verifyWebhookSignature(raw, signature, timestamp));
        assertFalse(cashfree.verifyWebhookSignature(raw + " ", signature, timestamp));
        assertFalse(cashfree.verifyPaymentSignature("order", "payment", signature));
    }

    private String hmac(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
