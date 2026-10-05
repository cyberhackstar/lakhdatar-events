package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseReleaseV211ContractTest {
    @Test
    void productionGuardRequiresPrivilegedMfa() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/ProductionConfigurationGuard.java"));
        assertTrue(source.contains("MFA_REQUIRED_FOR_PRIVILEGED must be true in production"));
    }

    @Test
    void enterpriseE2eGateIsPresentAndStagingOnly() throws Exception {
        String workflow = Files.readString(Path.of("../.github/workflows/e2e-staging.yml"));
        String config = Files.readString(Path.of("../e2e/playwright.config.js"));
        assertTrue(workflow.contains("enterprise-e2e-staging"));
        assertTrue(workflow.contains("environment: staging"));
        assertTrue(workflow.contains("Refusing live production origin"));
        assertTrue(config.contains("E2E_BASE_URL must use HTTPS"));
    }

    @Test
    void enterpriseOperationalControlsAreWired() throws Exception {
        String metrics = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/BusinessMetricsService.java"));
        String alerts = Files.readString(Path.of("../infra/monitoring/alerts.yml"));
        String smoke = Files.readString(Path.of("../infra/smoke/production-smoke.sh"));
        String production = Files.readString(Path.of("../.github/workflows/production.yml"));
        assertTrue(metrics.contains("lakhdatar.webhooks.dead_letters"));
        assertTrue(metrics.contains("lakhdatar.refunds.manual_review"));
        assertTrue(alerts.contains("LakhdatarWebhookDeadLetters"));
        assertTrue(alerts.contains("LakhdatarRefundManualReview"));
        assertTrue(smoke.contains("Content-Security-Policy"));
        assertTrue(smoke.contains("Invalid ticket token was not rejected"));
        assertTrue(production.contains("ENTERPRISE_CERTIFIED_SHA"));
    }

    @Test
    void activeReleaseVersionIsConsistent() throws Exception {
        String v = Files.readString(Path.of("../VERSION")).trim();
        assertTrue(Files.readString(Path.of("../frontend/package.json")).contains("\"version\": \"" + v + "\""));
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<version>" + v + "</version>"));
    }
}
