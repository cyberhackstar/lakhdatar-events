package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EventOperationsContractTest {
    @Test
    void issuedTicketsAndOrdersArePaginatedAndEventScoped() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AdminTicketQueryService.java"));
        String controller = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/AdminController.java"));
        assertTrue(controller.contains("/events/{eventId}/tickets"));
        assertTrue(controller.contains("/events/{eventId}/orders"));
        assertTrue(service.contains("limit ? offset ?"));
        assertTrue(service.contains("access.canManageEvent"));
        assertTrue(service.contains("t.event_id=?"));
    }
}
