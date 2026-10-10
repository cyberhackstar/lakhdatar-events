package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseReleaseQualificationContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    void providerRecoveryRechecksReceiptBeforeAdoptingProviderOrder() throws Exception {
        String source = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        int recovered = source.indexOf("var providerOrder=recovered.get();");
        assertTrue(recovered >= 0);
        String block = source.substring(recovered, Math.min(source.length(), recovered + 1200));
        assertTrue(block.contains("providerOrder.receipt()"));
        assertTrue(block.contains("providerOrder.amount()"));
        assertTrue(block.contains("providerOrder.currency()"));
    }

    @Test
    void loadSuiteHasHardServerErrorGatesAndSafeProductionGuards() throws Exception {
        String checkout = read("../infra/loadtest/checkout.js");
        String idem = read("../infra/loadtest/checkout-idempotency.js");
        String checkin = read("../infra/loadtest/checkin.js");
        String suite = read("../infra/loadtest/run-suite.sh");
        assertTrue(checkout.contains("checkout_server_errors"));
        assertTrue(checkout.contains("status >= 500"));
        assertTrue(idem.contains("idempotency_server_errors"));
        assertTrue(idem.contains("events\\.neelastack\\.com") || idem.contains("events.neelastack.com"));
        assertTrue(checkin.contains("CHECKIN_RATE"));
        assertTrue(suite.contains("--summary-export=\"/results/"));
    }

    @Test
    void enterpriseQualificationDocumentsHaDrAndCompleteScenarioSet() throws Exception {
        String matrix = read("../docs/ENTERPRISE-VALIDATION-MATRIX.md");
        String qualification = read("../docs/ENTERPRISE-RELEASE-QUALIFICATION.md");
        assertTrue(matrix.contains("checkout-idempotency.js"));
        assertTrue(matrix.contains("ticket-pdf.js"));
        assertTrue(matrix.contains("operations.js"));
        assertTrue(matrix.contains("Two independent application VMs") || qualification.contains("two application VMs"));
        assertTrue(qualification.contains("PITR"));
        assertTrue(qualification.contains("k6 summary JSON"));
    }

    @Test
    void haReferenceSeparatesApiAndWorkerAndUsesExternalState() throws Exception {
        String compose = read("../infra/ha/docker-compose.ha.example.yml");
        assertTrue(compose.contains("WORKER_ENABLED: \"false\""));
        assertTrue(compose.contains("WORKER_ENABLED: \"true\""));
        assertTrue(compose.contains("DB_URL: ${DB_URL:?DB_URL is required}"));
        assertTrue(compose.contains("REDIS_HOST: ${REDIS_HOST:?REDIS_HOST is required}"));
        assertFalse(compose.contains("deploy:\n      replicas:"));
    }

    @Test
    void flywayQualificationUsesApplyOrderInsteadOfLexicalVersionMax() throws Exception {
        String flyway = read("../infra/certification/flyway-qualification.sh");
        assertTrue(flyway.contains("ORDER BY installed_rank DESC LIMIT 1"));
        assertFalse(flyway.contains("SELECT max(version) FROM flyway_schema_history"));
    }

    @Test
    void dastWritesBasenameReportsToWritableMountedDirectory() throws Exception {
        String dast = read("../infra/security/dast-staging.sh");
        assertTrue(dast.contains("chmod 0777 \"$REPORT_ABS\""));
        assertTrue(dast.contains("-r zap-baseline.html"));
        assertTrue(dast.contains("-J zap-baseline.json"));
        assertFalse(dast.contains("-r /zap/wrk/zap-baseline.html"));
    }

    @Test
    void enterpriseLoadGateReportsMissingInputsWithoutLeakingValues() throws Exception {
        String gate = read("../infra/loadtest/enterprise-gate.sh");
        assertTrue(gate.contains("missing_runtime_inputs"));
        assertTrue(gate.contains("missing required runtime inputs"));
        assertTrue(gate.contains("LOADTEST_* secrets"));
        assertTrue(gate.contains("enterprise-gate-preflight.txt"));
    }

}
