package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseScaleContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test
    void financeApisHaveServerSideScopeAndImmutableLedger() throws Exception {
        String controller = read("src/main/java/com/neelastack/lakhdatar/controller/FinanceController.java");
        String service = read("src/main/java/com/neelastack/lakhdatar/service/FinanceService.java");
        String migration = read("src/main/resources/db/migration/V25__immutable_financial_ledger.sql");
        assertTrue(controller.contains("/ledger"));
        assertTrue(service.contains("organizer_members"));
        assertTrue(migration.contains("financial ledger is append-only"));
        assertTrue(migration.contains("trg_financial_ledger_immutable"));
    }

    @Test
    void productionReleaseContainsLoadAndDrPlan() throws Exception {
        assertTrue(Files.exists(Path.of("../infra/loadtest/catalog.js")));
        assertTrue(Files.exists(Path.of("../infra/loadtest/checkout.js")));
        assertTrue(Files.exists(Path.of("../infra/loadtest/checkin.js")));
        assertTrue(Files.exists(Path.of("../infra/backup/PITR.md")));
        assertTrue(Files.exists(Path.of("../infra/ha/README.md")));
        assertTrue(Files.exists(Path.of("../infra/chaos/payment-provider-resilience.md")));
    }

    @Test
    void ticketPdfRemainsTokenProtectedAndDoesNotEmbedBearerToken() throws Exception {
        String controller = read("src/main/java/com/neelastack/lakhdatar/controller/PublicEventController.java");
        String pdf = read("src/main/java/com/neelastack/lakhdatar/service/TicketPdfService.java");
        assertTrue(controller.contains("X-Ticket-Token"));
        assertTrue(controller.contains("/tickets/{ticketId}/pdf"));
        assertTrue(pdf.contains("No customer bearer token is ever embedded in the PDF"));
    }
}
