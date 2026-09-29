package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {
    private final RefreshTokenRepository refreshTokens;

    @Scheduled(fixedDelayString = "${app.auth.cleanup-sweep:3600000}")
    @Transactional
    public void sweep() {
        int removed = refreshTokens.deleteExpiredOrOldRevoked(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) log.info("Removed {} expired/revoked refresh tokens", removed);
    }
}
