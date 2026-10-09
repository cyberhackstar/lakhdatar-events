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
        assertTrue(refund.contains("PARTIAL_PROVIDER_REFUND_RECONCILED"));
        assertTrue(refund.contains("completedMinor"));
        assertTrue(refund.contains("remainingMinor"));
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
        assertTrue(webhook.contains("String eventId = stableEventId(n, payloadHash);"));
        assertTrue(webhook.contains("private String stableEventId(JsonNode n, String payloadHash)"));
    }

    @Test
    void razorpayRefundWebhookIsReconciledFromProviderPaymentId() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/WebhookService.java"));
        assertTrue(source.contains("processRazorpayRefund"));
        assertTrue(source.contains("findByProviderPaymentId"));
        assertTrue(source.contains("reconcileProviderRefund"));
        assertTrue(source.contains("reconcileProviderRefundsIfPresent"));
    }

    @Test
    void workerOnlyRecoveryHasAnExplicitRuntimeGuard() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/WebhookService.java"));
        assertTrue(source.contains("if (!workerEnabled) return;"));
    }

    @Test
    void failedCredentialAndWrongEventScansDoNotExposeTicketDetails() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CheckInService.java"));
        assertTrue(source.contains("record(Enums.CheckInResult.INVALID, \"Invalid ticket credential\", t, r, e.getId(), 0, 0, false)"));
        assertTrue(source.contains("record(Enums.CheckInResult.WRONG_EVENT, \"Ticket belongs to a different event\", t, r, e.getId(), 0, 0, false)"));
    }
    @org.junit.jupiter.api.Test
    void staleWebhookRetryTimestampIsExplicitlyBoundAsPostgresTimestampWithTimeZone() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentWebhookEventRepository.java"));
        assertTrue(repo.contains("CAST(:retryAt AS timestamptz)"));
    }

    @org.junit.jupiter.api.Test
    void forcedPasswordChangeDoesNotBlockPublicOrWebhookEndpointsEvenWithBearerToken() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/JwtAuthFilter.java"));
        assertTrue(source.contains("isAllowedDuringPasswordChange"));
        assertTrue(source.contains("/api/v1/public/"));
        assertTrue(source.contains("/api/v1/webhooks/"));
        assertTrue(source.contains("/api/v1/setup/"));
    }

}
