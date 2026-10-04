package com.neelastack.lakhdatar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private static final int MAX_LOCAL_KEYS = 20_000;
    private static final DefaultRedisScript<Long> RATE_SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]) end; return n", Long.class);
    private final StringRedisTemplate redis;
    private final Map<String,Bucket> local=new ConcurrentHashMap<>();
    private final com.neelastack.lakhdatar.config.AppProperties props;

    public boolean allow(String key,int limit,Duration window){
        return check(key,limit,window,props.rateLimit().failClosedOnRedisError());
    }

    /**
     * Same limit, but when Redis is unavailable it degrades to the per-instance local bucket
     * instead of rejecting. Used for gate scans, where a Redis blip must not lock people out of
     * an event. Login, checkout and other abuse-sensitive paths keep using {@link #allow}.
     */
    public boolean allowFailOpen(String key,int limit,Duration window){
        return check(key,limit,window,false);
    }

    private boolean check(String key,int limit,Duration window,boolean failClosed){
        String normalized = key == null ? "unknown" : key.trim();
        if (normalized.length() > 180) normalized = normalized.substring(0,180);
        try{
            String safeKey = sha256(normalized);
            String k="lk:rl:"+safeKey;
            Long n=redis.execute(RATE_SCRIPT, java.util.List.of(k), String.valueOf(Math.max(1, window.toMillis())));
            return n!=null && n<=limit;
        }catch(Exception ignored){
            if (failClosed) return false;
            return allowLocal(normalized,limit,window);
        }
    }

    private boolean allowLocal(String normalized,int limit,Duration window){
        String localKey = sha256(normalized);
        long now=System.nanoTime();
        if(local.size()>=MAX_LOCAL_KEYS && !local.containsKey(localKey)) {
            final long cutoff = now - window.toNanos();
            int removed = 0;
            for (var entry : local.entrySet()) {
                if (entry.getValue().windowStartNanos < cutoff && local.remove(entry.getKey(), entry.getValue())) {
                    if (++removed >= 256) break;
                }
            }
            if(local.size()>=MAX_LOCAL_KEYS && !local.containsKey(localKey)) return false;
        }
        Bucket b=local.compute(localKey,(k,v)->v==null||now-v.windowStartNanos>=window.toNanos()?new Bucket(now):v);
        int count=b.count.incrementAndGet();
        return count <= limit;
    }
    private String sha256(String value){
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Bucket{
        final long windowStartNanos; final AtomicInteger count=new AtomicInteger();
        Bucket(long start){windowStartNanos=start;}
    }
}
