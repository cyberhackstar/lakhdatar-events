package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Release-contract guards for the v1.9.54 CI qualification fixes. */
class ProductionHardeningV1_9_54ContractTest {
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
        String source = Files.readString(Path.of("src/test/java/com/neelastack/lakhdatar/service/MultiEventCatalogIntegrationTest.java"));
        assertTrue(source.contains("@TestConfiguration"));
        assertTrue(source.contains("integrationRazorpayGateway"));
        assertTrue(source.contains("e.setPaymentProvider(Enums.PaymentProvider.RAZORPAY)"));
        assertTrue(source.contains("@Order(-1000)"));
    }

    @Test
    void releaseVersionMatchesAllBuildManifests() throws Exception {
        String version = Files.readString(Path.of("../VERSION")).trim();
        String pom = Files.readString(Path.of("pom.xml"));
        String pkg = Files.readString(Path.of("../frontend/package.json"));
        assertEquals("1.9.54", version);
        assertTrue(pom.contains("<artifactId>lakhdatar-events</artifactId>"));
        assertTrue(pom.contains("<version>1.9.54</version>"));
        assertTrue(pkg.contains("\"version\": \"1.9.54\""));
    }
}
