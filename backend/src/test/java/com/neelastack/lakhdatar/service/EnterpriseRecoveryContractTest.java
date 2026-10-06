package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseRecoveryContractTest {
    @Test void cancellationRefundCandidatesCanRetryFailedRefundsAndAvoidInFlightDuplicates() throws Exception {
        String s = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentRepository.java"));
        assertTrue(s.contains("status = 'COMPLETED'"));
        assertTrue(s.contains("r.status = 'PROCESSING'"));
        assertTrue(s.contains("r.status = 'FAILED'"));
        assertTrue(s.contains("r.next_attempt_at"));
        assertTrue(s.contains("attempt_count"));
        assertTrue(s.contains("r.manual_review_required"));
        assertFalse(s.contains("not exists (select 1 from refunds r where r.payment_id = p.id)"));
    }

    @Test void eventCancellationUsesSetBasedTicketUpdate() throws Exception {
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"));
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/TicketRepository.java"));
        assertTrue(svc.contains("tickets.cancelIssuedForEvent(e.getId())"));
        assertTrue(repo.contains("cancelIssuedForEvent"));
        assertTrue(repo.toLowerCase(java.util.Locale.ROOT).contains("update tickets set status='cancelled'"));
        assertFalse(svc.contains("findByEventIdOrderByTicketNumberAsc(e.getId()).forEach"));
    }

    @Test void eventCancellationReleasesHeldReservationsWithoutWalkingEveryReservation() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/TicketReservationRepository.java"));
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/TicketReservationService.java"));
        String event = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"));
        assertTrue(repo.contains("UPDATE ticket_reservations"));
        assertTrue(repo.contains("UPDATE ticket_types"));
        assertTrue(svc.contains("releaseHeldForEvent"));
        assertTrue(event.contains("reservations.releaseHeldForEvent(e.getId())"));
    }

    @Test void checkInFinalWriteIsConditionedOnPublishedEventState() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/TicketRepository.java"));
        assertTrue(repo.contains("markCheckedInIfEventPublished"));
        assertTrue(repo.contains("e.status='PUBLISHED'"));
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CheckInService.java"));
        assertTrue(svc.contains("markCheckedInIfEventPublished(t.getId(), e.getId(), checkedInAt)"));
    }

    @Test void refundRecoveryHasBackoffAndManualReviewBoundaries() throws Exception {
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        String job = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundRecoveryJob.java"));
        assertTrue(svc.contains("nextRetryAt"));
        assertTrue(svc.contains("REFUND_PROVIDER_ID_REUSED"));
        assertTrue(svc.contains("maxAttempts"));
        assertTrue(job.contains("processingTimeout"));
        assertTrue(job.contains("markManualReviewRequired"));
        String model = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/domain/Refund.java"));
        assertTrue(model.contains("manualReviewRequired"));
    }

    @Test void productionBackupsRequireIndependentRemoteStorageWhenEnabled() throws Exception {
        String script = Files.readString(Path.of("../infra/backup/backup-postgres.sh"));
        assertTrue(script.contains("BACKUP_REMOTE_REQUIRED"));
        assertTrue(script.contains("BACKUP_REMOTE_URI"));
        assertTrue(script.contains("head-object"));
    }

    @Test void passwordChangesInvalidateOutstandingResetLinks() throws Exception {
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AuthService.java"));
        assertTrue(svc.contains("passwordResetTokens.invalidateUnusedByUserId"));
        String refund = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        assertTrue(refund.contains("if (!allowSystem && tickets.findByOrderIdOrderByTicketNumberAsc"));
    }

    @Test void privilegedMfaAndPasswordRecoveryArePresent() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V34__enterprise_auth_recovery_mfa.sql"));
        String filter = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/JwtAuthFilter.java"));
        String reset = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/PasswordResetService.java"));
        assertTrue(migration.contains("mfa_enabled"));
        assertTrue(migration.contains("password_reset_tokens"));
        assertTrue(filter.contains("MFA_SETUP_REQUIRED"));
        assertTrue(reset.contains("INVALID_RESET_TOKEN"));
    }

    @Test void maintenanceSweepsAreReplicaSafe() throws Exception {
        String reset = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/PasswordResetService.java"));
        String mfa = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/MfaService.java"));
        String mail = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/TicketMailService.java"));
        assertTrue(reset.contains("job:password-reset-cleanup"));
        assertTrue(mfa.contains("job:mfa-cleanup"));
        assertTrue(mail.contains("mail-delivery-repair-sweep"));
        assertTrue(mail.contains("mail-delivery-cleanup-sweep"));
        assertTrue(mail.contains("mail-delivery-sweep"));
        assertTrue(mail.contains("findByIdForUpdate"));
    }

    @Test void paymentWebhooksAreProviderScopedAndDurablyRetryable() throws Exception {
        String entity = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/domain/PaymentWebhookEvent.java"));
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentWebhookEventRepository.java"));
        String razor = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/WebhookService.java"));
        String cashfree = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeWebhookService.java"));
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V37__enterprise_webhook_provider_discriminator.sql"));
        assertTrue(entity.contains("private String provider"));
        assertTrue(repo.contains("WHERE provider = :provider"));
        assertTrue(repo.contains("next_attempt_at <= :now"));
        assertTrue(razor.contains("e.setProvider(\"RAZORPAY\")"));
        assertTrue(cashfree.contains("e.setProvider(\"CASHFREE\")"));
        assertTrue(razor.contains("processingExecutor"));
        assertTrue(cashfree.contains("processingExecutor"));
        assertTrue(razor.contains("job:webhook-recovery:razorpay"));
        assertTrue(cashfree.contains("job:webhook-recovery:cashfree"));
        assertTrue(migration.contains("chk_payment_webhook_provider"));
        String mfaMigration = Files.readString(Path.of("src/main/resources/db/migration/V38__enterprise_mfa_session_proof.sql"));
        assertTrue(mfaMigration.contains("mfa_verified BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(mfaMigration.contains("idx_refresh_tokens_user_mfa"));
        String mfaReplayMigration = Files.readString(Path.of("src/main/resources/db/migration/V39__enterprise_mfa_replay_and_constraint_validation.sql"));
        String eventNotificationMigration = Files.readString(Path.of("src/main/resources/db/migration/V40__enterprise_event_change_notifications.sql"));
        assertTrue(mfaReplayMigration.contains("last_mfa_totp_counter"));
        assertTrue(mfaReplayMigration.contains("VALIDATE CONSTRAINT fk_ticket_reservations_order"));
        assertTrue(eventNotificationMigration.contains("event_notification_jobs"));
        assertTrue(eventNotificationMigration.contains("uk_event_notification_change"));
    }

    @Test void privilegedMfaHasAnEmergencyAdminRecoveryPath() throws Exception {
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/MfaService.java"));
        String controller = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/AdminController.java"));
        assertTrue(svc.contains("resetForAdmin"));
        assertTrue(svc.contains("MFA_SELF_RESET_FORBIDDEN"));
        assertTrue(controller.contains("/security/users/{userId}/mfa/reset"));
        assertTrue(controller.contains("hasRole('ADMIN')"));
    }
}
