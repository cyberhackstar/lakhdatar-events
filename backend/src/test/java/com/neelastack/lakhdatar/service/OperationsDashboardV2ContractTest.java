package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OperationsDashboardV2ContractTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/neelastack/lakhdatar/controller/OperationsController.java");
    private static final Path SERVICE = Path.of("src/main/java/com/neelastack/lakhdatar/service/OperationsDashboardService.java");

    @Test
    void operationsDashboardIsAdminOnlyAndNoStore() throws Exception {
        String controller = Files.readString(CONTROLLER);
        assertTrue(controller.contains("/dashboard"));
        assertTrue(controller.contains("hasRole('ADMIN')"));
        assertTrue(controller.contains("CacheControl.noStore()"));
    }

    @Test
    void dashboardUsesAggregateQueriesAndDoesNotExposePii() throws Exception {
        String service = Files.readString(SERVICE);
        assertTrue(service.contains("select count(*)"));
        assertTrue(service.contains("sum(amount_minor)"));
        assertFalse(service.contains("customer_email"));
        assertFalse(service.contains("customer_phone"));
        assertFalse(service.contains("attendee_name"));
        assertFalse(service.contains("payload"));
        assertFalse(service.contains("provider_signature"));
    }
}
