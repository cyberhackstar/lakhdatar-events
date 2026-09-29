package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundRoleContractTest {
    @Test
    void eventManagersAreNotFinancialRefundApprovers() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RefundService.java"));
        assertTrue(src.contains("\"OWNER\".equalsIgnoreCase(m.getRole()) || \"FINANCE\".equalsIgnoreCase(m.getRole())"));
        assertFalse(src.contains("\"EVENT_MANAGER\".equalsIgnoreCase(m.getRole())"));
    }
}
