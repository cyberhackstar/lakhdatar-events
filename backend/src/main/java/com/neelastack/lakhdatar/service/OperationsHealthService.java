package com.neelastack.lakhdatar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Read-only operator view of infrastructure and recovery queues. No ticket/payment data is exposed here. */
@Service
@RequiredArgsConstructor
public class OperationsHealthService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${spring.application.name:lakhdatar-events-backend}") private String applicationName;
    @Value("${spring.application.version:1.9.40}") private String applicationVersion;

    public record Health(String application, String version, Instant checkedAt, Component database, Component redis,
                         Queues queues, long publishedEvents, long organizers, boolean workerEnabled) {}
    public record Component(String status, long latencyMs, String detail) {}
    public record Queues(long pendingPayments, long stalePayments, long providerOrderRecoveryPending,
                         long pendingRefunds, long webhookBacklog, long webhookStuck,
                         long heldReservations, long expiredReservations, long mailPending, long mailFailed) {}

    public Health health() {
        Instant now = Instant.now();
        Component db = database();
        Component rd = redis();
        Queues q = queues();
        Long events = scalar("select count(*) from events where status='PUBLISHED'");
        Long orgs = scalar("select count(*) from organizers");
        return new Health(applicationName, applicationVersion, now, db, rd, q, events == null ? 0 : events, orgs == null ? 0 : orgs, workerEnabled);
    }

    private Component database() {
        long start = System.nanoTime();
        try {
            Integer ok = jdbc.queryForObject("select 1", Integer.class);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Component(ok != null && ok == 1 ? "UP" : "DOWN", ms, "PostgreSQL query succeeded");
        } catch (Exception ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Component("DOWN", ms, "Database health query failed");
        }
    }

    private Component redis() {
        long start = System.nanoTime();
        try {
            String pong = redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Component("PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN", ms, "Redis ping succeeded");
        } catch (Exception ex) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Component("DOWN", ms, "Redis health query failed");
        }
    }

    private Queues queues() {
        long pendingPayments = scalar("select count(*) from payments where status in ('CREATED','PENDING','PAYMENT_INITIATED','AUTHORIZED')");
        long stalePayments = scalar("select count(*) from payments where status in ('CREATED','PENDING','PAYMENT_INITIATED','AUTHORIZED') and created_at < now() - interval '2 minutes'");
        long providerRecovery = scalar("select count(*) from payments where razorpay_order_state='RECOVERY_PENDING'");
        long pendingRefunds = scalar("select count(*) from refunds where status in ('REQUESTED','PROCESSING')");
        long webhookBacklog = scalar("select count(*) from payment_webhook_events where processed=false and processing=false and received_at < now() - interval '2 minutes'");
        long webhookStuck = scalar("select count(*) from payment_webhook_events where processing=true and processing_started_at < now() - interval '5 minutes'");
        long heldReservations = scalar("select count(*) from ticket_reservations where status='HELD' and expires_at > now()");
        long expiredReservations = scalar("select count(*) from ticket_reservations where status='HELD' and expires_at <= now()");
        long mailPending = scalar("select count(*) from ticket_mail_jobs where status in ('PENDING','PROCESSING')");
        long mailFailed = scalar("select count(*) from ticket_mail_jobs where status='FAILED'");
        return new Queues(pendingPayments, stalePayments, providerRecovery, pendingRefunds, webhookBacklog, webhookStuck, heldReservations, expiredReservations, mailPending, mailFailed);
    }

    private long scalar(String sql) {
        try { Long value = jdbc.queryForObject(sql, Long.class); return value == null ? 0L : value; }
        catch (Exception ex) { return 0L; }
    }
}
