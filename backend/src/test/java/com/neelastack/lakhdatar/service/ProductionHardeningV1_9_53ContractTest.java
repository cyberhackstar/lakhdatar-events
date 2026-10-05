package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProductionHardeningV1_9_53ContractTest {
    private String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    @Test
    void providerAttemptUsesDeclaredProviderPaymentErrorDescriptionAccessor() throws Exception {
        String order = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        String provider = read("src/main/java/com/neelastack/lakhdatar/service/PaymentGatewayProvider.java");
        assertTrue(provider.contains("String errorDescription"));
        assertTrue(order.contains("provider.errorDescription()"));
        assertFalse(order.contains("provider.message()"));
    }

    @Test
    void cashfreeWebhookNormalizesPaymentStatusAsEnum() throws Exception {
        String webhook = read("src/main/java/com/neelastack/lakhdatar/service/CashfreeWebhookService.java");
        assertTrue(webhook.contains("Enums.PaymentStatus normalizedStatus = normalizeAttemptStatus(status);"));
        assertTrue(webhook.contains("normalizedStatus.name().toLowerCase(Locale.ROOT)"));
        assertTrue(webhook.contains("Enums.PaymentStatus.CAPTURED == normalizedStatus"));
    }
}
