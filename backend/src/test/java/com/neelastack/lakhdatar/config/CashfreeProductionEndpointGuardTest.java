package com.neelastack.lakhdatar.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CashfreeProductionEndpointGuardTest {

    @Test
    void acceptsOfficialProductionEndpoint() {
        assertDoesNotThrow(() -> ProductionConfigurationGuard.requireCashfreeProductionBaseUrl(
                "https://api.cashfree.com/pg"));
        assertDoesNotThrow(() -> ProductionConfigurationGuard.requireCashfreeProductionBaseUrl(
                "https://api.cashfree.com/pg/"));
    }

    @Test
    void rejectsSandboxAndUnapprovedEndpoints() {
        List<String> invalidEndpoints = List.of(
                "https://sandbox.cashfree.com/pg",
                "http://api.cashfree.com/pg",
                "https://evil.example/pg",
                "https://api.cashfree.com/pg/v2",
                "https://api.cashfree.com:8443/pg",
                "https://api.cashfree.com/pg?redirect=https://evil.example",
                "https://user@api.cashfree.com/pg",
                "https://api.cashfree.com/pg#fragment",
                "",
                "not-a-url"
        );

        for (String endpoint : invalidEndpoints) {
            assertThrows(IllegalStateException.class,
                    () -> ProductionConfigurationGuard.requireCashfreeProductionBaseUrl(endpoint),
                    "Endpoint must be rejected: " + endpoint);
        }
    }
}