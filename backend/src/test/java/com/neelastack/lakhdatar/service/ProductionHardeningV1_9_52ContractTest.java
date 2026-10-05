package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProductionHardeningV1_9_52ContractTest {
    @Test
    void reconciliationAndRefundNeverUseArbitraryFirstProviderPayment() throws Exception {
        String recon = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/ReconciliationJob.java"));
        String refund = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        String order = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertFalse(recon.contains("fetchPaymentsForOrder(p.getProviderOrderId()).stream"));
        assertFalse(refund.contains("fetchPaymentsForOrder(payment.getProviderOrderId()).stream"));
        assertTrue(recon.contains("selectProviderPaymentForReconciliation"));
        assertTrue(recon.contains("Enums.PaymentStatus.COMPLETED"));
        assertTrue(refund.contains("MULTIPLE_CAPTURED_PAYMENTS"));
        assertTrue(order.contains("reconcileProviderRefundsIfPresent"));
        assertTrue(order.contains("REFUND_LEDGER_UNAVAILABLE"));
    }

    @Test
    void refundsSupportMoreThanOneProviderRefundPerPayment() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/RefundRepository.java"));
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V32__multiple_refunds_per_payment.sql"));
        assertTrue(repo.contains("findAllByPaymentIdOrderByCreatedAtAscIdAsc"));
        assertTrue(migration.contains("DROP INDEX IF EXISTS uq_refunds_payment"));
        assertTrue(migration.contains("DROP INDEX IF EXISTS uq_refunds_provider_receipt"));
    }

    @Test
    void apiMailPathDoesNotSubmitLocalWorkerWhenWorkerIsDisabled() throws Exception {
        String mail = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/TicketMailService.java"));
        assertTrue(mail.contains("if (workerEnabled) submitOrder(orderId);"));
    }


    @Test
    void receiptRecoveryImmediatelyProcessesQueuedRefunds() throws Exception {
        String order = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(order.contains("Provider payment captured during receipt-based checkout recovery"));
        assertTrue(order.contains("completeQueuedRefundIfNeeded(localPaymentId,"));
    }

    @Test
    void allWorkerOnlyScheduledJobsHaveConditionalRuntimeGuards() throws Exception {
        String base = "src/main/java/com/neelastack/lakhdatar/service/";
        for (String name : new String[]{"EventCancellationRefundJob.java","RefundRecoveryJob.java","RefreshTokenCleanupJob.java","ReservationExpiryJob.java","TicketMailService.java"}) {
            String source = Files.readString(Path.of(base + name));
            assertTrue(source.contains("@ConditionalOnProperty(") && source.contains("app.worker"), name + " must be worker-conditional");
            assertTrue(source.contains("workerEnabled") && source.contains("if (!workerEnabled"), name + " must have a runtime worker guard");
        }
        String webhook = Files.readString(Path.of(base + "WebhookService.java"));
        // WebhookService is required on API nodes to acknowledge and queue provider webhooks; only its recovery scheduler is worker-gated.
        assertTrue(webhook.contains("workerEnabled") && webhook.contains("if (!workerEnabled) return"));
        assertTrue(webhook.contains("@Scheduled"));
    }

    @Test
    void deploymentRequiresBackupForExistingReleaseMarker() throws Exception {
        String deploy = Files.readString(Path.of("../infra/deploy/deploy.sh"));
        assertTrue(deploy.contains("if [[ -n \"$PREVIOUS_TAG\" ]]; then"));
        assertTrue(deploy.contains("backup-postgres.sh\"") || deploy.contains("backup-postgres.sh"));
        assertTrue(deploy.contains("backup is mandatory and fail-closed"));
        assertTrue(deploy.contains("lakhdatar_pg_data"));
    }
}
