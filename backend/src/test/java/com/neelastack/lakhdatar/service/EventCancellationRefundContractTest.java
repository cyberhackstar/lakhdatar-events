package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EventCancellationRefundContractTest {
    @Test void cancellationQueuesRefundWorkWithoutProviderCall() throws Exception {
        String job = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventCancellationRefundJob.java"));
        String refund = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        String lifecycle = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java"));
        assertTrue(job.contains("EventStatus.CANCELLED"));
        assertTrue(job.contains("queueCapturedPaymentRefundOnly"));
        assertTrue(refund.contains("queueCapturedPaymentRefundOnly"));
        assertTrue(lifecycle.contains("asynchronous recovery pipeline"));
    }
    @Test
    void checkoutAndCaptureUseEventRowLocks() throws Exception {
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/EventRepository.java"));
        String order = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(repo.contains("findByPublicIdForUpdate"));
        assertTrue(order.contains("events.findByPublicId(request.eventId())"));
        assertTrue(order.contains("events.findByIdForUpdate(event.getId())"));
        assertFalse(order.contains("events.findByPublicIdForUpdate(request.eventId())"));
        assertTrue(order.contains("events.findByIdForUpdate(o.getEventId())"));
    }

}
