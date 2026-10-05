package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Release-contract guards for the v2 release qualification fixes. */
class ProductionHardeningV2ContractTest {
    @Test
    void teamAuthTestUsesBehavioralFilterCoverage() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/neelastack/lakhdatar/service/TeamAuthContractTest.java"));
        assertTrue(source.contains("MockHttpServletRequest"));
        assertTrue(source.contains("PASSWORD_CHANGE_REQUIRED"));
        assertFalse(source.contains("!req.getRequestURI().startsWith(\"/api/v1/auth/\")"),
                "forced-password-change test must not depend on implementation formatting");
    }

    @Test
    void multiEventIntegrationTestUsesOnlyATestPaymentProvider() throws Exception {
        String integrationSource = Files.readString(
                Path.of("src/test/java/com/neelastack/lakhdatar/service/IntegrationPaymentGatewayConfiguration.java"));
        String multiEventSource = Files.readString(
                Path.of("src/test/java/com/neelastack/lakhdatar/service/MultiEventCatalogIntegrationTest.java"));

        assertTrue(integrationSource.contains("@TestConfiguration"));
        assertTrue(integrationSource.contains("integrationRazorpayGateway"));
        assertTrue(integrationSource.contains("@Order(-1000)"));
        assertTrue(integrationSource.contains("Enums.PaymentProvider.RAZORPAY"));
        assertTrue(integrationSource.contains("isConfigured() { return true; }"));

        assertTrue(multiEventSource.contains("@Import(IntegrationPaymentGatewayConfiguration.class)"));
        assertTrue(multiEventSource.contains("e.setPaymentProvider(Enums.PaymentProvider.RAZORPAY)"));
    }

    @Test
    void releaseVersionMatchesAllBuildManifests() throws Exception {
        String version = Files.readString(Path.of("../VERSION")).trim();
        String pom = Files.readString(Path.of("pom.xml"));
        String pkg = Files.readString(Path.of("../frontend/package.json"));
        assertEquals("2.0.0", version);
        assertTrue(pom.contains("<artifactId>lakhdatar-events</artifactId>"));
        assertTrue(pom.contains("<version>2.0.0</version>"));
        assertTrue(pkg.contains("\"version\": \"2.0.0\""));
    }
}
