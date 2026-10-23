package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StalePaymentLifecycleContractTest {
    @Test void releasedReservationsAreRepairedByBoundedWorkerWithoutEditingPaymentState() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/OrderRepository.java"));
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/TicketReservationService.java"));
        String worker = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/ReservationExpiryJob.java"));
        assertTrue(repo.contains("findOpenOrdersWithoutLiveReservations"));
        assertTrue(repo.contains("Pageable"));
        assertTrue(service.contains("closeOpenOrderWithoutLiveReservation"));
        assertTrue(service.contains("order.setCheckoutSessionHash(null)"));
        assertTrue(service.contains("PaymentStatus.CAPTURED"));
        assertTrue(service.contains("PaymentStatus.REFUND_PENDING"));
        assertTrue(service.contains("does not alter the payment state"));
        assertTrue(worker.contains("stale.orders.examined"));
        assertTrue(worker.contains("cleanupStaleOpenOrders"));
        assertTrue(worker.contains("app.reservation.stale-order-cleanup-sweep"));
        assertTrue(worker.contains("staleOrderCleanupBatchSize"));
        assertTrue(worker.contains("job:reservation-expiry"));
        String config = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(config.contains("stale-order-cleanup-sweep: 60s"));
        assertTrue(config.contains("stale-order-cleanup-batch-size: 100"));
    }

    @Test void reconciliationRequiresProviderTerminalOrderBeforeClosingNoCapture() throws Exception {
        String worker = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/ReconciliationJob.java"));
        String orders = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        String repository = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentRepository.java"));
        assertTrue(worker.contains("gateway.fetchOrder"));
        assertTrue(worker.contains("reconcileTerminalUncapturedPayment"));
        assertTrue(worker.contains("markAttemptedQuietly"));
        assertTrue(orders.contains("TerminalPaymentOutcome.classify"));
        assertTrue(orders.contains("PAYMENT_PROVIDER_MISMATCH"));
        assertTrue(repository.contains("reconciliation-batch-size") == false); // tuning lives in application config, not query source
        assertTrue(repository.contains("findHistoricalClosedOrderReconciliationCandidates"));
        assertTrue(repository.contains("lastReconciledAt asc nulls first"));
        assertTrue(worker.contains("app.payment.reconciliation-backlog-sweep-ms"));
        assertTrue(worker.contains("historical-closed-order"));
    }
}
