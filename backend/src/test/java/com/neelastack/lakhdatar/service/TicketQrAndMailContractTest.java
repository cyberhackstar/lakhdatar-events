package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard: the ticket page renders {@code t.qrDataUri}. It was once missing from the API response,
 * which silently showed a broken QR to paying customers. Also pins the email-delivery safety properties.
 */
class TicketQrAndMailContractTest {
    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    @Test void ticketApiResponseCarriesTheQrImageTheFrontendRenders() throws Exception {
        String svc = read("src/main/java/com/neelastack/lakhdatar/service/TicketQueryService.java");
        assertTrue(svc.contains("String qrDataUri, BrandService.BrandView brand"));
        assertTrue(svc.contains("qr.pngDataUri(cred)"));
        String page = read("../frontend/src/app/features/ticket/ticket.component.ts");
        assertTrue(page.contains("t.qrDataUri"));
        String model = read("../frontend/src/app/core/api/api.models.ts");
        assertTrue(model.contains("qrDataUri: string;"));
    }

    @Test void paidAndComplimentaryTicketsBothStoreAVerifiableQrHash() throws Exception {
        assertTrue(read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java").contains("t.setQrCredentialHash(qr.hash(credential))"));
        assertTrue(read("src/main/java/com/neelastack/lakhdatar/service/ManagerTicketService.java").contains("t.setQrCredentialHash(qr.hash(credential))"));
    }

    @Test void paidOrderEmailIsQueuedAfterCommitAndCannotAffectTheSale() throws Exception {
        String order = read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
        assertTrue(order.contains("ticketMail.sendAfterCommit(o.getId())"));
        String mail = read("src/main/java/com/neelastack/lakhdatar/service/TicketMailService.java");
        assertTrue(mail.contains("TransactionSynchronizationManager.registerSynchronization"));
        assertTrue(mail.contains("afterCommit()"));
        assertTrue(mail.contains("catch (Exception ex)"), "send must swallow failures and report FAILED");
        assertFalse(mail.matches("(?s).*log\\.(info|warn|error|debug)\\([^;]*(getCustomerEmail|link|token)[^;]*\\);.*"), "no addresses/links/tokens in logs");
    }

    @Test void complimentaryIssuanceReportsTheRealEmailOutcome() throws Exception {
        String ctl = read("src/main/java/com/neelastack/lakhdatar/controller/ManagerTicketController.java");
        assertTrue(ctl.contains("result.withEmailStatus(emailStatus)"));
        assertTrue(ctl.contains("/orders/{orderId}/email"));
        String mail = read("src/main/java/com/neelastack/lakhdatar/service/TicketMailService.java");
        assertTrue(mail.contains("canManageEvent(o.getEventId()"), "resend must be scoped to the actor's events");
        assertTrue(mail.contains("ticket-resend:"), "resend is rate limited");
    }

    @Test void smtpIsOptionalAndCannotBreakReadiness() throws Exception {
        String yml = read("src/main/resources/application.yml");
        assertTrue(yml.contains("host: ${MAIL_HOST:}"));
        assertTrue(yml.contains("mail:\n      # An unreachable SMTP server must never take the API out of the load balancer.\n      enabled: false"));
        assertTrue(read("../.env.example").contains("MAIL_PASSWORD="));
        assertTrue(read("../docker-compose.yml").contains("MAIL_HOST:"));
        assertTrue(read("../infra/docker-compose.prod.yml").contains("MAIL_HOST:"));
    }
}
