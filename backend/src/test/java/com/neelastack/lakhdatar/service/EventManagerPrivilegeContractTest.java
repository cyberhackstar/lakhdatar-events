package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EventManagerPrivilegeContractTest {
    @Test
    void eventManagersCannotCreateOrEditEventConfiguration() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"));
        assertTrue(src.contains("private boolean organizerManagementRole"));
        assertTrue(src.contains("return \"ADMIN\".equals(role) || \"ORGANIZER\".equals(role);"));
        assertFalse(src.contains("return \"ADMIN\".equals(role) || \"ORGANIZER\".equals(role) || \"EVENT_MANAGER\".equals(role);"));
    }

    @Test
    void eventManagersCannotManageStaffAssignments() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AdminService.java"));
        assertTrue(src.contains("Only platform administrators or organizer owners can manage event staff"));
        assertFalse(src.contains("!\"EVENT_MANAGER\".equals(role)"));
    }
}
