package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class RefundIdempotencyContractTest {
    @Test
    void refundUsesProviderIdempotencyAndReceiptMatching() throws Exception {
        String razor = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RazorpayService.java"));
        String refund = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        String entity = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/domain/Refund.java"));
        require(razor.contains("X-Refund-Idempotency"), "Missing Razorpay refund idempotency header");
        require(razor.contains("n.path(\"receipt\").asText(null)"), "Refund receipt is not parsed");
        require(refund.contains("providerRefundId") && refund.contains("providerReceipt"), "Provider refund identity/receipt is not persisted and reconciled");
        require(refund.contains("reconcileProviderRefund"), "External provider refund reconciliation is missing");
        require(entity.contains("providerRefundId") && entity.contains("providerReceipt"), "Refund entity missing provider refund identity");
    }
    private static void require(boolean ok, String msg) { if (!ok) throw new IllegalStateException(msg); }
}
