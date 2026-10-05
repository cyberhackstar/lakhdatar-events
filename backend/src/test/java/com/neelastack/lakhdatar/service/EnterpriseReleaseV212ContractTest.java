package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseReleaseV212ContractTest {
    @Test
    void privilegedMfaResetRevokesTargetSessions() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/MfaService.java"));
        assertTrue(source.contains("refreshTokens.revokeAllActiveByUserId(target.getId(), now)"));
    }

    @Test
    void webhookStaleRecoveryIsProviderScoped() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentWebhookEventRepository.java"));
        String razorpay = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/WebhookService.java"));
        String cashfree = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeWebhookService.java"));
        assertTrue(repo.contains("WHERE provider = :provider"));
        assertTrue(repo.contains("@Param(\"provider\") String provider"));
        assertTrue(razorpay.contains("resetStaleProcessing(\"RAZORPAY\""));
        assertTrue(cashfree.contains("resetStaleProcessing(\"CASHFREE\""));
    }

    @Test
    void productionGuardHardFailsWithoutPrivilegedMfa() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/ProductionConfigurationGuard.java"));
        assertTrue(source.contains("MFA_REQUIRED_FOR_PRIVILEGED must be true in production"));
    }

    @Test
    void enterpriseCertificationGateIsFailClosed() throws Exception {
        String script = Files.readString(Path.of("../infra/certification/verify-enterprise-evidence.sh"));
        String workflow = Files.readString(Path.of("../.github/workflows/enterprise-release-qualification.yml"));
        assertTrue(script.contains("require_id HA_FAILOVER_EVIDENCE_ID"));
        assertTrue(script.contains("require_status HA_FAILOVER_STATUS"));
        assertTrue(script.contains("PRODUCTION_TOPOLOGY"));
        assertTrue(workflow.contains("environment: enterprise-certification"));
        assertTrue(workflow.contains("verify-enterprise-evidence.sh"));
    }

    @Test
    void statefulBrowserTestsCannotRetryAndConsumeStateTwice() throws Exception {
        String source = Files.readString(Path.of("../e2e/tests/critical-business.spec.js"));
        assertTrue(source.contains("mode: 'serial'"));
        assertTrue(source.contains("retries: 0"));
    }

    @Test
    void productionPromotionVerifiesSignedImages() throws Exception {
        String workflow = Files.readString(Path.of("../.github/workflows/production.yml"));
        String haWorkflow = Files.readString(Path.of("../.github/workflows/production-ha.yml"));
        assertTrue(workflow.contains("cosign verify"));
        assertTrue(workflow.contains("gh attestation verify"));
        assertTrue(haWorkflow.contains("cosign verify"));
        assertTrue(haWorkflow.contains("gh attestation verify"));
    }
}
