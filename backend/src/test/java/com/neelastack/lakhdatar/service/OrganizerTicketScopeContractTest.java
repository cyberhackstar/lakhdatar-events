package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OrganizerTicketScopeContractTest {
    @Test
    void topLevelIssuedTicketsUseServerEnforcedOrganizationAndEventScope() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AdminTicketQueryService.java"));
        String controller = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/AdminController.java"));
        String ui = Files.readString(Path.of("../frontend/src/app/features/admin/issued-tickets.component.ts"));
        assertTrue(controller.contains("@GetMapping(\"/tickets\")"));
        assertTrue(service.contains("om.role in ('OWNER','ORGANIZER')"));
        assertTrue(service.contains("event_manager_assignments ema"));
        assertTrue(service.contains("e.public_id=?"));
        assertTrue(service.contains("limit ? offset ?"));
        assertTrue(ui.contains("All events"));
        assertTrue(ui.contains("allIssuedTickets"));
    }
}
