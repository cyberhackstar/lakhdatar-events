package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshTokenReuseContractTest {
    @Test
    void rotatedRefreshTokenReuseRevokesRemainingSessions() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AuthService.java"));
        String repo = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/repository/RefreshTokenRepository.java"));
        assertTrue(service.contains("getReplacedByTokenHash() != null"));
        assertTrue(service.contains("revokeAllActiveByUserId"));
        assertTrue(repo.contains("revokeAllActiveByUserId"));
        // The reuse branch throws after revoking sessions; without noRollbackFor the revocation is rolled back.
        assertTrue(service.contains("@Transactional(noRollbackFor = ApiException.class)"));
    }
}
