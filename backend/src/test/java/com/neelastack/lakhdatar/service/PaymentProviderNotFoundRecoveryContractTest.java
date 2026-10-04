package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PaymentProviderNotFoundRecoveryContractTest {
    @Test
    void staleProviderOrderIsClearedOnlyOnExplicitProviderNotFound() throws Exception {
        String order = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        String razor = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RazorpayService.java"));
        String cashfree = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java"));
        assertTrue(order.contains("PAYMENT_PROVIDER_NOT_FOUND"));
        assertTrue(order.contains("p.setProviderOrderId(null)"));
        assertTrue(order.contains("p.setRazorpayOrderId(null)"));
        assertTrue(razor.contains("response.statusCode() == 404"));
        assertTrue(razor.contains("PAYMENT_PROVIDER_NOT_FOUND"));
        assertTrue(cashfree.contains("if (e.status() == HttpStatus.NOT_FOUND) return Optional.empty()"));
    }
}
