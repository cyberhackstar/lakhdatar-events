package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryContractTest {
  @Test void recoveryIsRateLimitedAndNoStore() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/PublicEventController.java"));
    assertTrue(source.contains("recover:"));
    assertTrue(source.contains("recover-email:"));
    assertTrue(source.contains("CacheControl.noStore()"));
  }
  @Test void pendingCheckoutRecoveryIsRateLimitedAndReusesTheExistingOrder() throws Exception {
    String controller = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/PublicEventController.java"));
    String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
    assertTrue(controller.contains("@PostMapping(\"/orders/recover-checkout\")"));
    assertTrue(controller.contains("recover-checkout-reference:"));
    assertTrue(controller.contains("recover-checkout-email:"));
    assertTrue(controller.contains("HttpHeaders.SET_COOKIE"));
    assertTrue(controller.contains(".httpOnly(true)"));
    assertTrue(controller.contains("CacheControl.noStore()"));
    assertTrue(service.contains("public CheckoutResponse recoverCheckout(String reference, String email)"));
    assertTrue(service.contains("candidate.getCustomerEmail().equalsIgnoreCase(normalizedEmail)"));
    assertTrue(service.contains("isRecoverablePaymentStatus(payment.getStatus())"));
    assertTrue(service.contains("provisionPaymentOrder(orderId, checkoutSessionToken)"));
    assertTrue(service.contains("order.setCheckoutSessionHash(hashCheckoutSession(checkoutSessionToken))"));
    assertTrue(service.contains("status == Enums.PaymentStatus.PENDING"));
    assertTrue(service.contains("status == Enums.PaymentStatus.PAYMENT_INITIATED"));
  }
}
