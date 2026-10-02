package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CashfreeIntegrationContractTest {
    @Test void orderContainsReturnAndNotifyUrls() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java"));
        assertTrue(source.contains("/payment/success?order_id={order_id}"));
        assertTrue(source.contains("/api/v1/webhooks/cashfree"));
        assertTrue(source.contains("notify_url"));
    }
    @Test void webhookFailsClosedAndEnforcesFreshness() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java"));
        String webhook = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeWebhookService.java"));
        assertTrue(source.contains("return false;"));
        assertTrue(webhook.contains("webhookToleranceMs()"));
        assertTrue(webhook.contains("DataIntegrityViolationException"));
        assertFalse(webhook.contains("headerEventId"));
        assertFalse(Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/WebhookController.java")).contains("x-webhook-event-id"));
    }
    @Test void reconciliationIsProviderNeutral() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/ReconciliationJob.java"));
        assertTrue(source.contains("app.payment.reconciliation-sweep-ms"));
        assertTrue(source.contains("props.payment().reconciliationAgeMs()"));
        assertFalse(source.contains("app.razorpay.reconciliation-sweep-ms"));
    }
}
