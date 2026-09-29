package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ManagerTicketContractTest {
    @Test void managerTicketProvenanceIsConstrainedByMigration() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V12__ticket_issuance_provenance.sql"));
        assertTrue(sql.contains("COMPLIMENTARY_MANAGER"));
        assertTrue(sql.contains("chk_ticket_issuer_consistency"));
        assertTrue(sql.contains("issued_by_user_id"));
    }

    @Test void managerIssuanceRequiresEventAssignment() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/ManagerTicketService.java"));
        assertTrue(src.contains("Only an assigned event manager"));
        assertTrue(src.contains("eventAccess.canManageEvent(event.getId(), actor.userId(), actor.role())"));
        assertTrue(src.contains("Enums.TicketSource.COMPLIMENTARY_MANAGER"));
    }

    @Test void scannerUsesEventScopedAccess() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CheckInService.java"));
        assertTrue(src.contains("access.canAccessEventAndGate"));
        int accessIndex = src.indexOf("access.canAccessEventAndGate");
        int closedIndex = src.indexOf("Enums.CheckInResult.EVENT_CLOSED");
        assertTrue(accessIndex >= 0 && accessIndex < closedIndex, "authorization must precede event-state probing");
    }

    @Test void sourceEnumContainsManagerComplimentaryTickets() {
        assertEquals("COMPLIMENTARY_MANAGER", Enums.TicketSource.COMPLIMENTARY_MANAGER.name());
    }
}
