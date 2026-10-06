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
    private final ScheduledExecutorService renewer = Executors.newScheduledThreadPool(4, new LockRenewalThreadFactory());

    public DistributedLockService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean withLock(String key, Duration ttl, Runnable action) {
        try (Handle handle = tryAcquire(key, ttl)) {
            if (!handle.acquired()) return false;
            action.run();
            return true;
        }
    }

    /** Result-bearing variant that keeps the lock heartbeat alive for the whole operation. */
    public <T> java.util.Optional<T> withLockOptional(String key, Duration ttl, java.util.function.Supplier<T> action) {
        try (Handle handle = tryAcquire(key, ttl)) {
            if (!handle.acquired()) return java.util.Optional.empty();
            return java.util.Optional.ofNullable(action.get());
        }
    }

    public Handle tryAcquire(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("Lock TTL must be positive");
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent("lk:lock:" + key, token, ttl);
            if (Boolean.TRUE.equals(acquired)) return new Handle("lk:lock:" + key, token, true, ttl);
            return new Handle("lk:lock:" + key, token, false, ttl);
        } catch (Exception ignored) {
            // Critical workflows must fail closed if the distributed lock backend is unavailable.
            // A process-local fallback is unsafe once more than one backend instance exists.
            return new Handle("lk:lock:" + key, token, false, ttl);
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
        private final ScheduledFuture<?> heartbeat;
        private Handle(String key, String token, boolean acquired, Duration ttl) {
            this.key = key; this.token = token; this.acquired = acquired;
            this.heartbeat = acquired ? startRenewal(ttl) : null;
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
            if (heartbeat != null) heartbeat.cancel(false);
            try { redis.execute(UNLOCK_SCRIPT, List.of(key), token); }
            catch (Exception ignored) { }
        }
    }
}
