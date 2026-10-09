package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regression guard for Spring Boot ECS throwable logging. */
class EnterpriseStructuredLoggingContractTest {
    @Test
    void throwableLogsNamespaceCustomErrorFieldsAwayFromEcsReservedErrorObject() {
        Logger logger = mock(Logger.class);
        LoggingEventBuilder builder = mock(LoggingEventBuilder.class);
        RuntimeException failure = new RuntimeException("do not serialize this message");
        when(logger.atError()).thenReturn(builder);
        when(builder.setMessage(any(String.class))).thenReturn(builder);
        when(builder.addKeyValue(any(String.class), any())).thenReturn(builder);
        when(builder.setCause(any(Throwable.class))).thenReturn(builder);

        com.neelastack.lakhdatar.config.EnterpriseLog.error(logger, "api.unhandled_exception", failure,
                "error.type", "RuntimeException", "error.message", "sensitive diagnostic");

        verify(builder).addKeyValue("failure.type", "RuntimeException");
        verify(builder).addKeyValue("failure.message", "[REDACTED]");
        verify(builder).setCause(failure);
        verify(builder).log();
        verify(builder, never()).addKeyValue(eq("error.type"), any());
        verify(builder, never()).addKeyValue(eq("error.message"), any());
    }


    @Test
    void scalarProviderDoesNotCollideWithNestedProviderFields() {
        Logger logger = mock(Logger.class);
        LoggingEventBuilder builder = mock(LoggingEventBuilder.class);
        when(logger.atWarn()).thenReturn(builder);
        when(builder.setMessage(any(String.class))).thenReturn(builder);
        when(builder.addKeyValue(any(String.class), any())).thenReturn(builder);

        com.neelastack.lakhdatar.config.EnterpriseLog.warn(logger, "payment.provider.http.failed",
                "event.category", "payment", "provider", "RAZORPAY",
                "provider.path", "/orders", "http.status_code", 401);

        verify(builder).addKeyValue("provider.name", "RAZORPAY");
        verify(builder, never()).addKeyValue(eq("provider"), eq("RAZORPAY"));
        verify(builder).addKeyValue("provider.path", "/orders");
        verify(builder).log();
    }

    @Test
    void sourceContractKeepsThrowableCauseAndFailureNamespace() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/EnterpriseLog.java"));
        assertTrue(source.contains("if (throwablePresent && key.startsWith(\"error.\"))"));
        assertTrue(source.contains("key = \"failure.\" + key.substring(\"error.\".length())"));
        assertTrue(source.contains("builder.setCause(error)"));
        assertTrue(source.contains("failure.message"), "failure message must retain the same redaction boundary");
        assertTrue(source.contains("providerHasNestedFields"));
        assertTrue(source.contains("key = \"provider.name\""));
    }

    @Test
    void operationsCenterReadsBothLegacyEcsAndFailureNamespaceFields() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OperationsLogService.java"));
        assertTrue(source.contains("firstTextAt(json, \"error.code\", \"failure.code\""));
        assertTrue(source.contains("firstTextAt(json, \"error.type\", \"failure.type\""));
        assertTrue(source.contains("firstTextAt(json, \"provider.name\", \"provider\""));
    }
}
