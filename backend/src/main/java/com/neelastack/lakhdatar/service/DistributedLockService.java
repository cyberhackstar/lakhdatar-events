package com.neelastack.lakhdatar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistributedLockService {
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;

    public Handle tryAcquire(String key, Duration ttl) {
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

    public final class Handle implements AutoCloseable {
        private final String key;
        private final String token;
        private final boolean acquired;
        private Handle(String key, String token, boolean acquired) {
            this.key = key; this.token = token; this.acquired = acquired;
        }

        public boolean acquired() { return acquired; }

        @Override
        public void close() {
            if (!acquired) return;
            try { redis.execute(UNLOCK_SCRIPT, List.of(key), token); }
            catch (Exception ignored) { }
        }
    }
}
