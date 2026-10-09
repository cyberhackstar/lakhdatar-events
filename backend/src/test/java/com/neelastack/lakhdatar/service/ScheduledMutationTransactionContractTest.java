package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures scheduled cleanup update/delete repository methods are transaction-safe. */
class ScheduledMutationTransactionContractTest {
    @Test
    void allScheduledCleanupMutationsDeclareTransactions() throws Exception {
        assertTransactional(Path.of("src/main/java/com/neelastack/lakhdatar/repository/MfaChallengeRepository.java"), "deleteExpiredOrUsed");
        assertTransactional(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PasswordResetTokenRepository.java"), "deleteExpiredOrUsed");
        assertTransactional(Path.of("src/main/java/com/neelastack/lakhdatar/repository/EventNotificationJobRepository.java"), "deleteHistory");
        assertTransactional(Path.of("src/main/java/com/neelastack/lakhdatar/repository/TicketMailJobRepository.java"), "deleteByStatusInAndUpdatedAtBefore");
        assertTransactional(Path.of("src/main/java/com/neelastack/lakhdatar/repository/PaymentWebhookEventRepository.java"), "deleteTerminalOlderThan");
        assertTransactional(Path.of("src/main/java/com/neelastack/lakhdatar/repository/RefreshTokenRepository.java"), "deleteExpiredOrOldRevoked");
    }

    private static void assertTransactional(Path file, String method) throws Exception {
        String s = Files.readString(file);
        int methodAt = s.indexOf(method + "(");
        assertTrue(methodAt >= 0, "Missing method " + method + " in " + file);
        int previousDeclarationEnd = s.lastIndexOf(';', methodAt);
        int transactionalAt = s.lastIndexOf("@Transactional", methodAt);
        assertTrue(transactionalAt > previousDeclarationEnd && transactionalAt < methodAt,
                "Missing @Transactional for " + method + " in " + file);
    }
}
