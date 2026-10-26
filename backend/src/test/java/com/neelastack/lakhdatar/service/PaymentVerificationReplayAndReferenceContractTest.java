package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PaymentVerificationReplayAndReferenceContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void confirmedReplayCanReportStateWithoutIssuingTicketCredentials() throws Exception {
        String order = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        int confirmed = order.indexOf("if(verifiedOrder.getStatus()==Enums.OrderStatus.CONFIRMED)");
        int tokenRequired = order.indexOf("if(req.checkoutSessionToken()==null", confirmed);
        assertTrue(confirmed >= 0, "confirmed replay branch must exist");
        assertTrue(tokenRequired > confirmed, "already-confirmed replay must not depend on a cookie that is cleared on success");
        String replayBlock = order.substring(confirmed, tokenRequired);
        assertTrue(replayBlock.contains("matchesCheckoutSession(verifiedOrder, req.checkoutSessionToken())"));
        assertTrue(replayBlock.contains("return confirmedStatusOnlyResponse(verifiedOrder)"));
        int statusOnly = order.indexOf("private VerifyResponse confirmedStatusOnlyResponse");
        int normalResponse = order.indexOf("public VerifyResponse response(Order o)", statusOnly);
        assertTrue(statusOnly > 0 && normalResponse > statusOnly);
        String statusOnlyMethod = order.substring(statusOnly, normalResponse);
        assertTrue(statusOnlyMethod.contains("List.of()"), "unauthenticated replay must not include tickets");
        assertTrue(statusOnlyMethod.contains("order.getOrderNumber()"));
        assertTrue(statusOnlyMethod.contains("null"), "transaction references require session-bound return or email-scoped recovery");
        assertFalse(statusOnlyMethod.contains("accessTokens.issue"), "a provider order ID is not authorization for ticket links");
    }

    @Test
    void orderAndProviderTransactionReferencesAreReturnedAndRecoveryIsEmailScoped() throws Exception {
        String order = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        String controller = read("src/main/java/com/neelastack/lakhdatar/controller/PublicEventController.java");
        assertTrue(order.contains("String transactionId"), "verification response exposes a provider transaction ID when known");
        assertTrue(order.contains("findFirstByPaymentIdAndStatusInOrderByLastSeenAtDesc"),
                "transaction references can be recovered from captured provider attempts before the primary row is reconciled");
        assertTrue(order.contains("payments.findByProviderPaymentId(normalized)"), "recovery accepts the provider transaction/payment reference");
        assertTrue(order.contains("paymentAttempts.findByProviderPaymentId(normalized)"), "recovery can resolve payment-attempt references");
        assertTrue(order.contains("if(!o.getCustomerEmail().equalsIgnoreCase(email.trim()))"));
        assertTrue(controller.contains("o.transactionId()"), "recovery API returns the transaction ID alongside the order number");
    }

    @Test
    void successfulEmailShowsTheOrderNumberAndKnownTransactionReference() throws Exception {
        String mail = read("src/main/java/com/neelastack/lakhdatar/service/TicketMailService.java");
        assertTrue(mail.contains("Neelastack order number:</strong>"));
        assertTrue(mail.contains("Payment transaction ID:</strong>"));
        assertTrue(mail.contains("payments.findByOrderId(o.getId())"));
        assertTrue(mail.contains("findFirstByPaymentIdAndStatusInOrderByLastSeenAtDesc"),
                "receipt uses a successful provider attempt if the primary payment reference is not yet linked");
        assertTrue(mail.contains(".append(esc(o.getOrderNumber()))"), "order reference is HTML escaped");
        assertTrue(mail.contains(".append(esc(transactionId))"), "provider reference is HTML escaped");
    }

    @Test
    void resultAndRecoveryScreensMakeReferencesCopyableAndRouteToRecovery() throws Exception {
        String models = read("../frontend/src/app/core/api/api.models.ts");
        String result = read("../frontend/src/app/features/payment-result/payment-result.component.ts");
        String recovery = read("../frontend/src/app/features/recover/recover.component.ts");
        assertTrue(models.contains("transactionId?: string | null;"));
        assertTrue(result.contains("NEELASTACK ORDER NUMBER"));
        assertTrue(result.contains("PAYMENT TRANSACTION ID"));
        assertTrue(result.contains("copyOrder()"));
        assertTrue(result.contains("copyTransaction()"));
        assertTrue(result.contains("Recover my tickets"));
        assertTrue(recovery.contains("Neelastack order number:"));
        assertTrue(recovery.contains("copyOrderNumber()"));
        assertTrue(recovery.contains("copyTransactionId()"));
        assertTrue(recovery.contains("Payment transaction ID:"));
    }
}
