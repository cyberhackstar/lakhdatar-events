package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProviderOrderRecoveryContractTest {
    @Test
    void recoverySchedulerLivesOnOrderService() throws Exception {
        Path order = Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        Path standalone = Path.of("src/main/java/com/neelastack/lakhdatar/service/ProviderOrderRecoveryJob.java");
        String source = Files.readString(order);
        assertTrue(source.contains("@Scheduled(fixedDelayString = \"${app.razorpay.order-recovery-sweep:30000}\")"));
        assertTrue(source.contains("recoverMissingProviderOrders"));
        assertFalse(Files.exists(standalone), "Standalone recovery component must not reintroduce the stale-classpath regression");
    }
}
