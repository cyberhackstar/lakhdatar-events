package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InitialAdminSetupContractTest {
    @Test void setupIsOneTimeAndDoesNotSeedAnEvent() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/InitialAdminSetupService.java"));
        assertTrue(service.contains("findByIdForUpdate"));
        assertTrue(service.contains("getInitialAdminCompletedAt()"));
        assertTrue(service.contains("setInitialAdminCompletedAt(Instant.now())"));
        assertTrue(service.contains("users.existsByRole(Enums.UserRole.ADMIN)"));
        assertFalse(service.contains("Event e"));
    }
    @Test void setupTokenUsesConstantTimeComparison() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/InitialAdminSetupService.java"));
        assertTrue(service.contains("MessageDigest.isEqual"));
    }
}
