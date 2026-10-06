package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Cross-cutting release contract checks for the 2.0.20 enterprise certification gates. */
class EnterpriseReleaseV220ContractTest {
    private static String normalized(String source) {
        return source.replaceAll("\\s+", " ").trim();
    }

    private static void assertAppearsBefore(String source, String first, String second) {
        int a = source.indexOf(first);
        int b = source.indexOf(second);
        assertTrue(a >= 0, "Missing expected source fragment: " + first);
        assertTrue(b >= 0, "Missing expected source fragment: " + second);
        assertTrue(a < b, "Expected lock-order fragment to appear before: " + second);
    }

    private static void assertPatternAppearsBefore(String source, String regex, String second) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(source);
        assertTrue(matcher.find(), "Missing expected source pattern: " + regex);
        int b = source.indexOf(second);
        assertTrue(b >= 0, "Missing expected source fragment: " + second);
        assertTrue(matcher.start() < b, "Expected lock acquisition pattern to appear before: " + second);
    }

    @Test void cashfreeUsesIdempotentServerSideProviderCalls() throws Exception {
        String provider = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java"));
        String webhook = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeWebhookService.java"));
        assertTrue(provider.contains("x-idempotency-key"));
        assertTrue(provider.contains("deterministicIdempotencyKey(\"order\", receipt)"));
        assertTrue(provider.contains("UUID.nameUUIDFromBytes"));
        assertTrue(provider.contains("MessageDigest.isEqual"));
        assertTrue(webhook.contains("x-webhook-signature") || webhook.contains("verifyWebhookSignature"));
        assertTrue(webhook.contains("persistIfAbsent"));
        assertTrue(webhook.contains("markProcessed"));
        assertTrue(webhook.contains("resetStaleProcessing"));
    }

    @Test void checkoutDoesNotSerializeAllBuyersOnTheEventLookupLock() throws Exception {
        String order = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(order.contains("events.findByPublicId(request.eventId())"));
        assertTrue(order.contains("events.findByIdForUpdate(event.getId())"));
        assertFalse(order.contains("events.findByPublicIdForUpdate(request.eventId())"));
    }

    @Test void checkoutAndInventoryAdminUseTheSameLockOrder() throws Exception {
        String checkout = normalized(Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"))).toLowerCase(java.util.Locale.ROOT);
        String admin = normalized(Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"))).toLowerCase(java.util.Locale.ROOT);

        assertTrue(checkout.contains("requestedtypes.sort(comparator.comparing(tickettyperequest::tickettypeid))"));
        assertPatternAppearsBefore(checkout,
                "reservationservice\\.reserve\\(\\w+\\.tickettypeid\\(\\),\\s*\\w+\\.quantity\\(\\)\\)",
                "events.findbyidforupdate(event.getid())");

        assertTrue(admin.contains("tickettype t = tickettypes.findbyidforupdate(found.getid()).orelsethrow()"));
        assertAppearsBefore(admin,
                "tickettypes.findbyidforupdate(found.getid()).orelsethrow()",
                "events.findbyidforupdate(t.geteventid())");

        assertTrue(admin.contains("if (t == transition.cancel)"));
        assertTrue(admin.contains("tickettypes.findbyeventidforupdateorderbyidasc(eventid)"));
        assertAppearsBefore(admin,
                "tickettypes.findbyeventidforupdateorderbyidasc(eventid)",
                "event e = events.findbyidforupdate(eventid)");
    }

    @Test void mfaAttemptsPersistAndTotpReplayIsRejected() throws Exception {
        String mfa = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/MfaService.java"));
        String user = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/domain/User.java"));
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/UserRepository.java"));
        assertTrue(mfa.contains("@Transactional(noRollbackFor = ApiException.class)"));
        assertTrue(mfa.contains("matchingTotpCounter"));
        assertTrue(mfa.contains("matchedCounter <= lastUsedCounter"));
        assertTrue(user.contains("lastMfaTotpCounter"));
        assertTrue(repo.contains("findByIdForUpdate"));
    }

    @Test void loginRateLimitIsScopedByClientAndIdentity() throws Exception {
        String auth = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AuthService.java"));
        assertTrue(auth.contains("login-client:"));
        assertTrue(auth.contains("login-combo:"));
    }

    @Test void distributedLocksRenewEvenForDirectTryAcquireUsers() throws Exception {
        String locks = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/DistributedLockService.java"));
        assertTrue(locks.contains("this.heartbeat = acquired ? startRenewal(ttl) : null"));
        assertTrue(locks.contains("heartbeat.cancel(false)"));
    }

    @Test void productionRequiresTransactionalMailAndActuatorIsEdgeBlocked() throws Exception {
        String guard = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/ProductionConfigurationGuard.java"));
        String edge = Files.readString(Path.of("../edge/nginx.conf"));
        assertTrue(guard.contains("MAIL_HOST"));
        assertTrue(guard.contains("MAIL_FROM"));
        assertTrue(edge.contains("location ^~ /actuator/ { return 404; }"));
    }

    @Test void reservationSweepAndMigrationsAreBacklogSafe() throws Exception {
        String job = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/ReservationExpiryJob.java"));
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/TicketReservationRepository.java"));
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V39__enterprise_mfa_replay_and_constraint_validation.sql"));
        String notificationMigration = Files.readString(Path.of("src/main/resources/db/migration/V40__enterprise_event_change_notifications.sql"));
        String notificationService = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventNotificationService.java"));
        String eventManagement = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"));
        assertTrue(job.contains("sweep-batch-size"));
        assertTrue(job.contains("sweep-max-batches"));
        assertTrue(job.contains("while (batches <"));
        assertTrue(repo.contains("Pageable"));
        assertTrue(migration.contains("VALIDATE CONSTRAINT fk_ticket_reservations_order"));
        assertTrue(notificationMigration.contains("uk_event_notification_change"));
        assertTrue(notificationMigration.contains("status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'SKIPPED')"));
        assertTrue(notificationService.contains("job:event-notifications"));
        assertTrue(notificationService.contains("MAX_ATTEMPTS"));
        assertTrue(eventManagement.contains("queueCancellation"));
        assertTrue(eventManagement.contains("queueDetailsChange"));
    }

    @Test void scannerAndQrOperationalSafeguardsAreBounded() throws Exception {
        String qr = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/QrCredentialService.java"));
        String scanner = Files.readString(Path.of("../frontend/src/app/features/scanner/scanner.component.ts"));
        assertTrue(qr.contains("MAX_PNG_CACHE_ENTRIES = 1000"));
        assertTrue(scanner.contains("checkedInAt"));
        assertTrue(scanner.contains("DatePipe"));
    }

    @Test void paymentProviderCapacityProtectionIsPresent() throws Exception {
        String guard = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/PaymentProviderGuard.java"));
        String router = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/PaymentGatewayRouter.java"));
        String props = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/AppProperties.java"));
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(guard.contains("Semaphore"));
        assertTrue(guard.contains("PAYMENT_PROVIDER_CIRCUIT_OPEN"));
        assertTrue(guard.contains("PAYMENT_PROVIDER_BUSY"));
        assertTrue(router.contains("guard.wrap(provider)"));
        assertTrue(props.contains("maxConcurrent"));
        assertTrue(props.contains("failureThreshold"));
        assertTrue(props.contains("bulkheadAcquireTimeoutMs"));
        assertTrue(props.contains("circuitOpenSeconds"));
        assertTrue(yaml.contains("PAYMENT_PROVIDER_MAX_CONCURRENT"));
        assertTrue(yaml.contains("PAYMENT_PROVIDER_FAILURE_THRESHOLD"));
    }

    @Test void versionManifestIsCurrent() throws Exception {
        String version = Files.readString(Path.of("../VERSION")).trim();
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"));
        assertTrue(pom.contains("<version>" + version + "</version>"));
    }
    @Test void haAndPitrCertificationGatesRequireLiveEvidence() throws Exception {
        String ha = Files.readString(Path.of("../infra/ha/verify-enterprise-ha.sh"));
        String pitr = Files.readString(Path.of("../infra/backup/verify-pitr-readiness.sh"));
        assertTrue(ha.contains("HA_INGRESS_HEALTH_URL is required"));
        assertTrue(ha.contains("curl") && ha.contains("--fail"));
        assertTrue(ha.contains("DB_EXTERNAL_TLS"));
        assertTrue(ha.contains("REDIS_EXTERNAL_TLS"));
        assertTrue(pitr.contains("pg_stat_archiver"));
        assertTrue(pitr.contains("PITR_MAX_ARCHIVE_AGE_SECONDS"));
        assertTrue(pitr.contains("last_archived_time"));
    }

}
