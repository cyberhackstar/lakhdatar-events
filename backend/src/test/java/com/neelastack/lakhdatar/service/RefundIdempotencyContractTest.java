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
        require(refund.contains("r.receipt()"), "Provider refund matching is not receipt-based");
        require(refund.contains("getProviderReceipt"), "Local provider receipt is not persisted/used");
        require(entity.contains("providerReceipt"), "Refund entity missing provider receipt");
    }
    private static void require(boolean ok, String msg) { if (!ok) throw new IllegalStateException(msg); }
}
