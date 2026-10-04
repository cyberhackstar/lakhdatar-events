package com.neelastack.lakhdatar.service;

import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class DistributedLockService {
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(new LockRenewalThreadFactory());

    public DistributedLockService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean withLock(String key, Duration ttl, Runnable action) {
        try (Handle handle = tryAcquire(key, ttl)) {
            if (!handle.acquired()) return false;
            ScheduledFuture<?> heartbeat = handle.startRenewal(ttl);
            try {
                action.run();
                return true;
            } finally {
                heartbeat.cancel(false);
            }
        }
    }

    public Handle tryAcquire(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Lock TTL must be positive");
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent("lk:lock:" + key, token, ttl);
            if (Boolean.TRUE.equals(acquired)) return new Handle("lk:lock:" + key, token, true);
            return new Handle("lk:lock:" + key, token, false);
        } catch (Exception ignored) {
            // Critical workflows must fail closed if the distributed lock backend is unavailable.
            // A process-local fallback is unsafe once more than one backend instance exists.
            return new Handle("lk:lock:" + key, token, false);
        }
    }

    @PreDestroy
    void shutdown() {
        renewer.shutdownNow();
    }

    private static final class LockRenewalThreadFactory implements ThreadFactory {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "lakhdatar-lock-renewer");
            t.setDaemon(true);
            return t;
        }
    }

    public final class Handle implements AutoCloseable {
        private final String key;
        private final String token;
        private final boolean acquired;
        private Handle(String key, String token, boolean acquired) {
            this.key = key; this.token = token; this.acquired = acquired;
        }

        public boolean acquired() { return acquired; }

        private ScheduledFuture<?> startRenewal(Duration ttl) {
            long everyMs = Math.max(1000L, Math.max(1000L, ttl.toMillis() / 3));
            return renewer.scheduleAtFixedRate(() -> {
                try {
                    redis.execute(RENEW_SCRIPT, List.of(key), token, Long.toString(ttl.toMillis()));
                } catch (Exception ignored) {
                    // The action still fails closed at acquisition time; renewal failures do not
                    // delete another owner's lock. A later expiry permits another instance to proceed.
                }
            }, everyMs, everyMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            if (!acquired) return;
            try { redis.execute(UNLOCK_SCRIPT, List.of(key), token); }
            catch (Exception ignored) { }
        }
    }
}
