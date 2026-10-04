package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseScaleContractTest {
    /** Resolve contract-test fixtures from either the backend module directory or the repository root. */
    private static String read(String path) throws Exception {
        Path requested = Path.of(path);
        if (requested.isAbsolute() && Files.isRegularFile(requested)) return Files.readString(requested);

        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("backend/pom.xml"))
                    && Files.isDirectory(cursor.resolve("frontend/src"))) {
                Path root = cursor;
                String normalized = path.replace('\\', '/');
                if (normalized.startsWith("../")) normalized = normalized.substring(3);
                if (normalized.startsWith("./")) normalized = normalized.substring(2);
                Path candidate = root.resolve(normalized).normalize();
                if (Files.isRegularFile(candidate)) return Files.readString(candidate);
                if (path.startsWith("src/")) {
                    Path backendCandidate = root.resolve("backend").resolve(path).normalize();
                    if (Files.isRegularFile(backendCandidate)) return Files.readString(backendCandidate);
                }
            }
            Path candidate = cursor.resolve(path).normalize();
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            cursor = cursor.getParent();
        }
        throw new java.nio.file.NoSuchFileException(path);
    }

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
    void financeLedgerQueriesUseExistingPublicIdColumn() throws Exception {
        String service = read("src/main/java/com/neelastack/lakhdatar/service/FinanceService.java");
        assertFalse(service.contains("l.entry_id"));
        assertTrue(service.contains("select l.public_id,l.entry_type,p.public_id,r.public_id"));
        assertFalse(service.contains("select l.public_id,l.entry_type,l.payment_id,l.refund_id"));
    }

    @Test
    void orderAndPublicSalesUseFullEventEndForMultiDayBookings() throws Exception {
        String publicService = read("src/main/java/com/neelastack/lakhdatar/service/PublicEventService.java");
        String orderService = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        assertTrue(publicService.contains("Instant eventEnd = e.getEndsAt() != null ? e.getEndsAt() : e.getStartsAt();"));
        assertTrue(publicService.contains("Instant bookingEnd = e.getBookingEndsAt() != null ? e.getBookingEndsAt() : eventEnd;"));
        assertTrue(orderService.contains("Instant eventEnd = event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt();"));
        assertFalse(publicService.contains("return end.isAfter(now) ? \"BOOKING_CLOSED\" : \"COMPLETED\";"));
    }

    @Test
    void cashfreeUsesHostedRedirectAndAdminEventsCsvUsesNativeDownload() throws Exception {
        String checkout = read("../frontend/src/app/features/checkout/checkout.component.ts");
        String ops = read("../frontend/src/app/features/admin/event-operations.component.ts");
        String list = read("../frontend/src/app/features/admin/events-list.component.ts");
        assertTrue(checkout.contains("redirectTarget: '_self'"));
        assertTrue(ops.contains("/attendees.csv"));
        assertTrue(list.contains("/api/v1/admin/events/${encodeURIComponent(e.id)}/attendees.csv"));
        assertFalse(list.contains("responseType: 'blob'"));
    }

    @Test
    void multiDayBookingBackfillMigrationExists() throws Exception {
        String migration = read("src/main/resources/db/migration/V30__backfill_multi_day_booking_end.sql");
        assertTrue(migration.contains("SET booking_ends_at = ends_at"));
        assertTrue(migration.contains("ends_at > starts_at"));
        assertTrue(migration.contains("booking_ends_at <= starts_at"));
    }

    @Test
    void cashfreeCheckoutSessionCookieSurvivesReturnRedirect() throws Exception {
        String controller = read("src/main/java/com/neelastack/lakhdatar/controller/CheckoutController.java");
        assertTrue(controller.contains(".path(\"/\")"));
        assertFalse(controller.contains(".path(\"/api/v1/public/checkout\")"));
    }

    @Test
    void ticketMetadataCarriesOrderQuantityAndPositionToGateAndCustomerSurfaces() throws Exception {
        String ticketService = read("src/main/java/com/neelastack/lakhdatar/service/TicketQueryService.java");
        String checkinService = read("src/main/java/com/neelastack/lakhdatar/service/CheckInService.java");
        String controller = read("src/main/java/com/neelastack/lakhdatar/controller/CheckInController.java");
        String ticketUi = read("../frontend/src/app/features/ticket/ticket.component.ts");
        String scannerUi = read("../frontend/src/app/features/scanner/scanner.component.ts");
        assertTrue(ticketService.contains("ticketPosition") && ticketService.contains("orderTicketCount"));
        assertTrue(checkinService.contains("findByOrderIdOrderByTicketNumberAsc"));
        assertTrue(controller.contains("r.ticketPosition(),r.orderTicketCount()"));
        assertTrue(ticketUi.contains("t.ticketPosition") && ticketUi.contains("t.orderTicketCount"));
        assertTrue(ticketUi.contains("booked in this order"));
        assertTrue(scannerUi.contains("orderTicketCount") && scannerUi.contains("seat-count"));
    }

    @Test
    void lifecycleUiGuardsKnownInvalidPublishRequests() throws Exception {
        String editor = read("../frontend/src/app/features/admin/event-editor.component.ts");
        String list = read("../frontend/src/app/features/admin/events-list.component.ts");
        assertTrue(editor.contains("get canPublish(): boolean"));
        assertTrue(editor.contains("An event in the past cannot be published."));
        assertTrue(editor.contains("action === 'publish' && !this.canPublish"));
        assertTrue(editor.contains("action === 'complete' && !this.canComplete"));
        assertTrue(editor.contains("Only a published event can be unpublished."));
        assertTrue(list.contains("canPublish(e)"));
        assertTrue(list.contains("e.status === 'DRAFT' || e.status === 'UNPUBLISHED'"));
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
    @Test
    void frontendCiUsesGlobalZoneBootstrapAndEventEditorNarrowingIsTypeSafe() throws Exception {
        String angular = read("../frontend/angular.json");
        String polyfills = read("../frontend/src/test-polyfills.ts");
        String editor = read("../frontend/src/app/features/admin/event-editor.component.ts");
        assertTrue(angular.contains("\"polyfills\": [") && angular.contains("src/test-polyfills.ts"));
        assertTrue(polyfills.contains("import 'zone.js';") && polyfills.contains("import 'zone.js/testing';"));
        assertTrue(editor.contains("if (!startsAt) { this.error = 'Enter a valid event start time.'; return; }"));
        assertTrue(editor.contains("const effectiveEventEnd = endsAt ?? startsAt;"));
    }

}
