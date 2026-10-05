package com.neelastack.lakhdatar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Read-only, low-cardinality operator snapshot for the platform operations center.
 * No customer PII, payment secrets, provider tokens or raw webhook payloads are exposed.
 * Queries are aggregate-only so the console remains safe to poll from multiple operator sessions.
 */
@Service
@RequiredArgsConstructor
public class OperationsDashboardService {
    private final JdbcTemplate jdbc;
    private final OperationsHealthService healthService;

    public record Kpis(
            long grossCaptured24hMinor,
            long refunds24hMinor,
            long orders24h,
            long successfulPayments24h,
            long failedPayments24h,
            long ticketsIssued24h,
            long checkIns24h,
            long activeEvents,
            long upcomingEvents
    ) {}

    public record Dashboard(
            OperationsHealthService.Health health,
            Kpis kpis,
            Instant generatedAt
    ) {}

    public Dashboard snapshot() {
        OperationsHealthService.Health health = healthService.health();
        Kpis kpis = new Kpis(
                scalar("select coalesce(sum(amount_minor),0)::bigint from payments where status in ('CAPTURED','COMPLETED') and updated_at >= now() - interval '24 hours'"),
                scalar("select coalesce(sum(amount_minor),0)::bigint from refunds where status='COMPLETED' and created_at >= now() - interval '24 hours'"),
                scalar("select count(*)::bigint from orders where created_at >= now() - interval '24 hours'"),
                scalar("select count(*)::bigint from payments where status in ('CAPTURED','COMPLETED') and updated_at >= now() - interval '24 hours'"),
                scalar("select count(*)::bigint from payments where status in ('FAILED','CANCELLED') and updated_at >= now() - interval '24 hours'"),
                scalar("select count(*)::bigint from tickets where created_at >= now() - interval '24 hours'"),
                scalar("select count(*)::bigint from ticket_checkins where created_at >= now() - interval '24 hours' and result='ACCEPTED'"),
                scalar("select count(*)::bigint from events where status='PUBLISHED' and starts_at <= now() and (ends_at is null or ends_at >= now())"),
                scalar("select count(*)::bigint from events where status='PUBLISHED' and starts_at > now()")
        );
        return new Dashboard(health, kpis, Instant.now());
    }

    private long scalar(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception ignored) {
            // Dashboard KPIs are advisory. Dependency health itself remains fail-closed in Health.
            return 0L;
        }
    }
}
