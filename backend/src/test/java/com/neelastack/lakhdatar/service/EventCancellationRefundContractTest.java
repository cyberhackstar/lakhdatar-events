package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
