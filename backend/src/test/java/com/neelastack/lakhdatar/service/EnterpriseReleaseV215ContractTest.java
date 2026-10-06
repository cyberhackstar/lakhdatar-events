package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EnterpriseReleaseV215ContractTest {
    @Test void mfaProofIsPersistedAndCarriedByJwt() throws Exception {
        String token = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/domain/RefreshToken.java"));
        String principal = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/UserPrincipal.java"));
        String jwt = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/JwtService.java"));
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V38__enterprise_mfa_session_proof.sql"));
        assertTrue(token.contains("mfaVerified"));
        assertTrue(principal.contains("boolean mfaVerified"));
        assertTrue(jwt.contains("claim(\"mfa\""));
        assertTrue(jwt.contains("requireIssuer"));
        assertTrue(jwt.contains("requireAudience"));
        assertTrue(migration.contains("mfa_verified"));
    }

    @Test void privilegedRefreshCannotElevateAnUnverifiedSession() throws Exception {
        String auth = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AuthService.java"));
        String filter = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/JwtAuthFilter.java"));
        assertTrue(auth.contains("!current.isMfaVerified()"));
        assertTrue(auth.contains("MFA_REAUTH_REQUIRED"));
        assertTrue(filter.contains("token.mfaVerified()"));
        assertTrue(filter.contains("MFA_VERIFICATION_REQUIRED"));
        assertTrue(filter.contains("isAllowedDuringMfaFlow"));
        assertFalse(filter.contains("uri.startsWith(\"/api/v1/auth/\")"));
    }

    @Test void jwtAudienceAndIssuerAreExplicitlyConfigured() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yml.contains("issuer: ${JWT_ISSUER:neelastack-events}"));
        assertTrue(yml.contains("audience: ${JWT_AUDIENCE:neelastack-events-web}"));
    }

    @Test void schedulerAndLockRenewalAreSizedForConcurrentWorkers() throws Exception {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        String lock = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/DistributedLockService.java"));
        assertTrue(yml.contains("size: ${SCHEDULER_POOL_SIZE:8}"));
        assertTrue(lock.contains("newScheduledThreadPool(4"));
    }

    @Test void currentHaTopologyCannotClaimUndeployedNodes() throws Exception {
        String verify = Files.readString(Path.of("../infra/ha/verify-enterprise-ha.sh"));
        String env = Files.readString(Path.of("../infra/ha/.env.ha.example"));
        assertTrue(verify.contains("HA_NODE_COUNT\" == 2"));
        assertTrue(env.contains("events.neelastack.com,monitor.neelastack.com"));
    }
    @Test void latestCompileRegressionFixesAreExplicitlyPresent() throws Exception {
        String eventManagement = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"));
        String mfa = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/MfaService.java"));
        String haCompose = Files.readString(Path.of("../infra/ha/docker-compose.ha.example.yml"));
        String flyway = Files.readString(Path.of("../infra/certification/flyway-qualification.sh"));
        assertTrue(eventManagement.contains("private static final Logger log"));
        assertTrue(eventManagement.contains("EnterpriseLog.info(log"));
        assertTrue(mfa.contains("private final DistributedLockService distributedLocks"));
        assertTrue(mfa.contains("distributedLocks.withLock(\"job:mfa-cleanup\""));
        assertTrue(haCompose.contains("JWT_ISSUER: ${JWT_ISSUER:?JWT_ISSUER is required}"));
        assertTrue(haCompose.contains("JWT_AUDIENCE: ${JWT_AUDIENCE:?JWT_AUDIENCE is required}"));
        assertTrue(flyway.contains("source tree latest migration is V$LATEST_MIGRATION"));
        assertTrue(flyway.contains("EXPECTED_LATEST_MIGRATION=\"$LATEST_MIGRATION\""));
    }

}
