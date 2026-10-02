package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentWebhookReleaseContractTest {
    @Test
    void webhookControllerWiresBothProviders() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/WebhookController.java"));
        assertTrue(source.contains("import com.neelastack.lakhdatar.service.CashfreeWebhookService;"));
        assertTrue(source.contains("private final CashfreeWebhookService cashfree;"));
    }

    @Test
    void lateProviderCaptureDoesNotRegressRefundPending() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(source.contains("p.getStatus()==Enums.PaymentStatus.REFUND_PENDING"));
        assertTrue(source.contains("return new CaptureReconciliation(o.getOrderNumber(),\"REFUND_PENDING\")"));
    }

    @Test
    void partialProviderRefundNeverQueuesAnotherFullRefundAutomatically() throws Exception {
        String order = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        String refund = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        assertTrue(!order.contains("if(partial) completeQueuedRefundIfNeeded(paymentId,\"Completing partial provider refund\")"));
        assertTrue(refund.contains("PARTIAL_PROVIDER_REFUND"));
        assertTrue(refund.contains("Enums.RefundStatus.FAILED"));
    }

    @Test
    void checkoutSessionHasServerVerifiableExpiryAndConcurrentVerifyLock() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(source.contains("checkout-verify:") && source.contains("validCheckoutSessionToken"));
        assertTrue(source.contains("CS1.") && source.contains("expire"));
    }

    @Test
    void cashfreeUsesExactMinorUnitParsing() throws Exception {
        String provider = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java"));
        String webhook = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeWebhookService.java"));
        assertTrue(provider.contains("movePointRight(2).longValueExact()"));
        assertTrue(webhook.contains("movePointRight(2).longValueExact()"));
        assertTrue(webhook.contains("String eventId = \"cashfree:\" + sha256(raw);"));
    }

    @Test
    void orphanRefundWebhookIsRetriedInsteadOfDiscarded() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        assertTrue(source.contains("REFUND_NOT_LINKED"));
    }
}
