package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProviderOrderRecoveryContractTest {
    @Test
    void missingProviderOrderRecoveryOnlySelectsPayableOrders() throws Exception {
        String repository = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentRepository.java"));
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        String normalizedRepository = repository.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        String normalizedService = service.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");

        assertTrue(normalizedRepository.contains("select p from payment p, order o"));
        assertTrue(normalizedRepository.contains("and o.status in :orderstatuses"),
                "Missing-provider recovery must exclude expired, cancelled, and confirmed orders");
        assertTrue(repository.contains("@Param(\"orderStatuses\") Collection<Enums.OrderStatus> orderStatuses"));
        assertTrue(normalizedService.contains(
                "list.of(enums.orderstatus.created,enums.orderstatus.awaiting_payment)"),
                "Only newly created and awaiting-payment orders can be recovered");
    }

    @Test
    void freshProviderOrdersSkipReceiptLookupUntilAPreviousAttemptMayHaveReachedTheGateway() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        String cashfree = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CashfreeGatewayProvider.java"));
        String normalizedService = service.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");

        assertTrue(normalizedService.contains("string priorproviderorderstate=p.getrazorpayorderstate();"),
                "Keep the pre-request state to distinguish a fresh creation from recovery");
        assertTrue(normalizedService.contains("if(!\"not_created\".equalsignorecase(context.providerorderstate()))"),
                "Only ambiguous previous attempts should perform receipt-based lookup before retrying");
        assertTrue(cashfree.contains("payment.provider.order_lookup.miss"),
                "A missing receipt during recovery is diagnostic, not a provider outage");
        assertTrue(cashfree.contains("call(req(\"/orders/\" + enc(id)).GET().build(), notFoundIsExpected)"));
    }

    @Test
    void recoverySchedulerLivesOnOrderService() throws Exception {
        Path order = Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        Path standalone = Path.of("src/main/java/com/neelastack/lakhdatar/service/ProviderOrderRecoveryJob.java");
        String source = Files.readString(order);
        assertTrue(source.contains("@Scheduled(fixedDelayString = \"${app.razorpay.order-recovery-sweep:30000}\")"));
        assertTrue(source.contains("recoverMissingProviderOrders"));
        assertFalse(Files.exists(standalone), "Standalone recovery component must not reintroduce the stale-classpath regression");
    }
}
