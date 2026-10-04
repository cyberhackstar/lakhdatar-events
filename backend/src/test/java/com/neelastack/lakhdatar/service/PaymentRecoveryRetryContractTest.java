package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRecoveryRetryContractTest {
    @Test
    void providerNotFoundAllowsSafeRetryInsteadOfPermanentCheckoutBlock() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(source.contains("if(!\"NOT_CREATED\".equalsIgnoreCase(context.providerOrderState()))"));
        assertTrue(source.contains("p.setRazorpayOrderState(\"NOT_CREATED\")"));
        assertTrue(source.contains("Optional<PaymentGatewayProvider.ProviderOrder> recovered"));
        assertFalse(source.contains("throw new ApiException(HttpStatus.CONFLICT,\"PAYMENT_PROVIDER_UNCERTAIN\",\"Payment gateway order creation is awaiting safe reconciliation. Please retry later or start a new checkout.\")"));
        assertTrue(source.contains("findOrderByReceipt(context.orderNumber())"));
    }
}
