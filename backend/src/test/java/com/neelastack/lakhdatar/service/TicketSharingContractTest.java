package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TicketSharingContractTest {
    @Test
    void ticketUiProvidesShareAndPdfActions() throws Exception {
        String ticket = Files.readString(Path.of("../frontend/src/app/features/ticket/ticket.component.ts"));
        String result = Files.readString(Path.of("../frontend/src/app/features/payment-result/payment-result.component.ts"));
        assertTrue(ticket.contains("Share ticket"));
        assertTrue(ticket.contains("Save as PDF"));
        assertTrue(ticket.contains("navigator.share"));
        assertTrue(ticket.contains("this.route.snapshot.fragment || ''"));
        assertTrue(ticket.contains("window.print()"));
        assertFalse(ticket.contains(".ticket-actions,.brand-header{display:none!important}"));
        assertTrue(result.contains("shareTicket"));
        assertTrue(result.contains("?print=1#access="));
    }
}
