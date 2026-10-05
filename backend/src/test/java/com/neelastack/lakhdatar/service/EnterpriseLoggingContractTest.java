package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Release guards for structured, searchable and privacy-safe production logging. */
class EnterpriseLoggingContractTest {
    @Test
    void enterpriseLoggerHasDefensiveRedactionAndBoundedValues() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/EnterpriseLog.java"));
        assertTrue(source.contains("MAX_VALUE_LENGTH = 512"));
        assertTrue(source.contains("password"));
        assertTrue(source.contains("authorization"));
        assertTrue(source.contains("signature"));
        assertTrue(source.contains("[REDACTED]"));
    }

    @Test
    void requestLifecycleLogsOutcomeDurationAndStatus() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/RequestLifecycleLoggingFilter.java"));
        assertTrue(source.contains("http.request.completed"));
        assertTrue(source.contains("http.status_code"));
        assertTrue(source.contains("http.response.duration_ms"));
        assertTrue(source.contains("event.outcome"));
        assertTrue(source.contains("SLOW_REQUEST_MS"));
    }

    @Test
    void logApiIsAdminOnlyNoStoreAndBounded() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/OperationsController.java"));
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OperationsLogService.java"));
        String lifecycle = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/ApplicationLifecycleLogging.java"));
        assertTrue(controller.contains("/logs"));
        assertTrue(controller.contains("hasRole('ADMIN')"));
        assertTrue(controller.contains("CacheControl.noStore()"));
        assertTrue(service.contains("MAX_LIMIT = 200"));
        assertTrue(service.contains("MAX_SINCE_MINUTES = 24 * 60"));
        assertTrue(service.contains("fetchLimit"));
        assertTrue(service.contains("filter(e -> normalizedLevel.equals(e.level()))"));
        assertTrue(service.contains("json.path(\"event\").path(\"action\")"));
        assertTrue(service.contains("http://loki:3100"));
        assertFalse(service.contains("request.body"));
        assertFalse(service.contains("response.body"));
        assertTrue(lifecycle.contains("application.ready"));
        assertTrue(lifecycle.contains("application.stopping"));
        String frontend = Files.readString(Path.of("../frontend/src/app/features/monitor-console.component.ts"));
        assertTrue(frontend.contains("Live logs"));
        assertTrue(frontend.contains("Polling every 5 seconds"));
        assertTrue(frontend.contains("Correlation"));
    }

    @Test
    void keyOperationalFlowsEmitStructuredEventsWithoutSecretsOrPii() throws Exception {
        String[] names = {"AuthService.java", "OrderService.java", "CheckInService.java", "WebhookService.java",
                "RefundService.java", "TicketReservationService.java", "RazorpayService.java", "CashfreeGatewayProvider.java",
                "TicketMailService.java", "CloudinaryAssetService.java"};
        for (String name : names) {
            String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/" + name));
            assertTrue(source.contains("EnterpriseLog."), name + " should emit structured operational events");
        }
        String auth = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AuthService.java"));
        int successIndex = auth.indexOf("EnterpriseLog.info(log, \"auth.login.succeeded\"");
        assertTrue(successIndex >= 0);
        String successEvent = auth.substring(successIndex, Math.min(auth.length(), successIndex + 280));
        assertFalse(successEvent.contains("email"));
        assertFalse(successEvent.contains("password"));
    }
}
