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

    /** Compare contract snippets without making the test depend on line wrapping/indentation. */
    private static boolean containsNormalized(String content, String expected) {
        return content.replaceAll("\\s+", " ").contains(expected.replaceAll("\\s+", " ").trim());
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
    void cashfreeUsesHostedRedirectAndAdminEventsCsvUsesAuthenticatedBlobDownload() throws Exception {
        String checkout = read("../frontend/src/app/features/checkout/checkout.component.ts");
        String ops = read("../frontend/src/app/features/admin/event-operations.component.ts");
        String list = read("../frontend/src/app/features/admin/events-list.component.ts");
        String api = read("../frontend/src/app/core/api/api.service.ts");
        assertTrue(checkout.contains("redirectTarget: '_self'"));
        assertTrue(api.contains("attendeesCsv(eventId: string)"));
        assertTrue(ops.contains("this.api.attendeesCsv(this.eventId)"));
        assertTrue(list.contains("this.api.attendeesCsv(e.id)"));
        assertFalse(ops.contains("a.href = `/api/v1/admin/events/"));
        assertFalse(list.contains("a.href = `/api/v1/admin/events/"));
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
    void productionBrowserNeverTargetsPublicBackendPortAndCashfreeReturnIsRecoverable() throws Exception {
        String apiToken = read("../frontend/src/app/core/api/api.tokens.ts");
        String auth = read("../frontend/src/app/core/auth/auth.service.ts");
        String env = read("../frontend/src/environments/environment.prod.ts");
        String paymentResult = read("../frontend/src/app/features/payment-result/payment-result.component.ts");
        String recover = read("../frontend/src/app/features/recover/recover.component.ts");
        String orderService = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        assertTrue(env.contains("apiBaseUrl: '/api/v1'"));
        assertTrue(containsNormalized(apiToken, "if (typeof window !== 'undefined' && environment.production) return '/api/v1';"));
        assertTrue(auth.contains("inject(API_BASE_URL)"));
        assertFalse(apiToken.contains("events.neelastack.com:8080"));
        // Contract actual recovery behavior, not a human-readable comment. Comments are not runtime guarantees
        // and should not be a release gate.
        int errorHandlerStart = paymentResult.indexOf("error: e =>");
        int errorHandlerEnd = errorHandlerStart >= 0 ? paymentResult.indexOf("\n      });", errorHandlerStart) : -1;
        assertTrue(errorHandlerStart >= 0 && errorHandlerEnd > errorHandlerStart,
                "payment-result verification error handler must exist");
        String errorHandler = paymentResult.substring(errorHandlerStart, errorHandlerEnd);
        assertFalse(errorHandler.contains("navigateByUrl('/recover'"),
                "transient provider verification failures must not force navigation to recovery");
        assertTrue(containsNormalized(errorHandler,
                "this.result = { orderPublicId: '', orderNumber: recoveryOrder || providerOrderId, status: 'PENDING', tickets: [] };"));
        assertTrue(containsNormalized(errorHandler,
                "if (this.returnAttempt < this.maxReturnAttempts) this.scheduleReturnVerification(providerOrderId);"));
        assertTrue(paymentResult.contains("maxReturnAttempts = 8"));
        assertTrue(recover.contains("Cashfree transaction ID"));
        assertTrue(containsNormalized(recover, "finalize(() => { this.loading = false; })"));
        assertTrue(orderService.contains("findByProviderPaymentId(normalized)"));
        assertTrue(containsNormalized(orderService, "if(verifiedOrder.getStatus()==Enums.OrderStatus.CONFIRMED) return response(verifiedOrder);"));
    }

    @Test
    void frontendCiUsesSupportedZoneBootstrapAndEventEditorNarrowingIsTypeSafe() throws Exception {
        String angular = read("../frontend/angular.json");
        String specTsConfig = read("../frontend/tsconfig.spec.json");
        String polyfills = read("../frontend/src/test-polyfills.ts");
        String editor = read("../frontend/src/app/features/admin/event-editor.component.ts");
        assertFalse(angular.contains("\"polyfills\": [\n              \"src/test-polyfills.ts\""),
                "Angular unit-test builder must not use the unsupported polyfills option");
        assertTrue(specTsConfig.contains("\"src/test-polyfills.ts\""));
        assertTrue(polyfills.contains("import 'zone.js';") && polyfills.contains("import 'zone.js/testing';"));
        assertTrue(editor.contains("if (!startsAt) { this.error = 'Enter a valid event start time.'; return; }"));
        assertTrue(editor.contains("const effectiveEventEnd = endsAt ?? startsAt;"));
        String checkout = read("../frontend/src/app/features/checkout/checkout.component.ts");
        String header = read("../frontend/src/app/shared/site-header.component.ts");
        String brandMark = read("../frontend/src/app/shared/brand-mark.component.ts");
        String csvList = read("../frontend/src/app/features/admin/events-list.component.ts");
        String login = read("../frontend/src/app/features/auth/login.component.ts");
        String paymentResult = read("../frontend/src/app/features/payment-result/payment-result.component.ts");
        assertTrue(checkout.contains("color-scheme:light"));
        assertTrue(checkout.contains("-webkit-text-fill-color:#171219"));
        assertTrue(checkout.contains("maxlength=\"10\""));
        assertTrue(header.contains("[height]=\"48\""));
        assertTrue(brandMark.contains("/assets/neelastack-logo.png"));
        assertTrue(Files.isRegularFile(Path.of("../frontend/src/assets/neelastack-logo.png")));
        assertTrue(csvList.contains("attendeesCsv"));
        assertTrue(login.contains("this.form.markAllAsTouched()") && login.contains("Validators.minLength(8)"));
        assertTrue(paymentResult.contains("const recoveryOrder = this.booking.getRecoveryHint();"));
    }

}
