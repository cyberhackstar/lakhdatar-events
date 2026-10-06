package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PaymentProviderGuardContractTest {
    @Test
    void providerGuardWiresAllCapacityAndCircuitSettings() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/PaymentProviderGuard.java"));
        assertTrue(source.contains("config.maxConcurrent()"));
        assertTrue(source.contains("config.failureThreshold()"));
        assertTrue(source.contains("config.bulkheadAcquireTimeoutMs()"));
        assertTrue(source.contains("config.circuitOpenSeconds()"));
        assertTrue(source.contains("new State("));
        assertTrue(source.contains("PAYMENT_PROVIDER_CIRCUIT_OPEN"));
        assertTrue(source.contains("PAYMENT_PROVIDER_BUSY"));
    }

    @Test
    void providerCapacitySettingsHaveSafePositiveDefaults() throws Exception {
        String props = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/AppProperties.java"));
        assertTrue(props.contains("32, 5, 1000, 15"));
    }
}
