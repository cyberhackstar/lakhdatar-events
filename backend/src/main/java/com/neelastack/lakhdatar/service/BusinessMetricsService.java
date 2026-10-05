package com.neelastack.lakhdatar.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-cardinality business and dependency gauges for the SRE dashboards.
 * Values are refreshed from the same read-only operator health model used by /admin/ops/health.
 */
@Component
public class BusinessMetricsService {
    private final OperationsHealthService ops;
    private final MeterRegistry registry;

    private final AtomicLong databaseUp = new AtomicLong();
    private final AtomicLong redisUp = new AtomicLong();
    private final AtomicLong databaseLatencyMs = new AtomicLong();
    private final AtomicLong redisLatencyMs = new AtomicLong();
    private final AtomicLong workerEnabled = new AtomicLong();
    private final AtomicLong publishedEvents = new AtomicLong();
    private final AtomicLong organizers = new AtomicLong();
    private final AtomicLong pendingPayments = new AtomicLong();
    private final AtomicLong stalePayments = new AtomicLong();
    private final AtomicLong providerRecovery = new AtomicLong();
    private final AtomicLong pendingRefunds = new AtomicLong();
    private final AtomicLong webhookBacklog = new AtomicLong();
    private final AtomicLong webhookStuck = new AtomicLong();
    private final AtomicLong webhookDeadLetters = new AtomicLong();
    private final AtomicLong refundManualReview = new AtomicLong();
    private final AtomicLong heldReservations = new AtomicLong();
    private final AtomicLong expiredReservations = new AtomicLong();
    private final AtomicLong mailPending = new AtomicLong();
    private final AtomicLong mailFailed = new AtomicLong();
    private final AtomicLong publishableEvents = new AtomicLong();
    private final AtomicLong activeTicketTypes = new AtomicLong();
    private final AtomicLong checkIns15m = new AtomicLong();
    private final AtomicLong orders5m = new AtomicLong();
    private final AtomicLong lastRefresh = new AtomicLong();
    private final Counter refreshErrors;

    public BusinessMetricsService(OperationsHealthService ops, MeterRegistry registry) {
        this.ops = ops;
        this.registry = registry;
        this.refreshErrors = Counter.builder("lakhdatar.observability.refresh.errors")
                .description("Number of failed business-metric refresh cycles")
                .register(registry);
    }

    @PostConstruct
    void registerMeters() {
        gauge("lakhdatar.dependency.database.up", databaseUp, "PostgreSQL dependency health (1=up, 0=down)");
        gauge("lakhdatar.dependency.redis.up", redisUp, "Redis dependency health (1=up, 0=down)");
        gauge("lakhdatar.dependency.database.latency", databaseLatencyMs, "PostgreSQL probe latency in milliseconds");
        gauge("lakhdatar.dependency.redis.latency", redisLatencyMs, "Redis probe latency in milliseconds");
        gauge("lakhdatar.worker.enabled", workerEnabled, "Whether the application worker tier is enabled");
        gauge("lakhdatar.events.published", publishedEvents, "Published event count");
        gauge("lakhdatar.organizers.total", organizers, "Organizer count");
        gauge("lakhdatar.payments.pending", pendingPayments, "Payments awaiting final state");
        gauge("lakhdatar.payments.stale", stalePayments, "Payments awaiting final state for more than two minutes");
        gauge("lakhdatar.payments.provider_recovery_pending", providerRecovery, "Provider-order recovery queue depth");
        gauge("lakhdatar.refunds.pending", pendingRefunds, "Pending or processing refunds");
        gauge("lakhdatar.webhooks.backlog", webhookBacklog, "Unprocessed webhook backlog older than two minutes");
        gauge("lakhdatar.webhooks.stuck", webhookStuck, "Webhook jobs stuck in processing for more than five minutes");
        gauge("lakhdatar.webhooks.dead_letters", webhookDeadLetters, "Webhook jobs that exhausted retry limits");
        gauge("lakhdatar.refunds.manual_review", refundManualReview, "Refunds requiring operator review");
        gauge("lakhdatar.reservations.held", heldReservations, "Currently held ticket reservations");
        gauge("lakhdatar.reservations.expired", expiredReservations, "Expired HELD reservations awaiting cleanup");
        gauge("lakhdatar.mail.pending", mailPending, "Pending/processing ticket mail jobs");
        gauge("lakhdatar.mail.failed", mailFailed, "Failed ticket mail jobs");
        gauge("lakhdatar.events.publishable", publishableEvents, "Draft or unpublished events that pass basic publication prerequisites");
        gauge("lakhdatar.ticket_types.active", activeTicketTypes, "Active ticket types");
        gauge("lakhdatar.checkins.last15m", checkIns15m, "Ticket check-ins during the last fifteen minutes");
        gauge("lakhdatar.orders.last5m", orders5m, "Orders created during the last five minutes");
        gauge("lakhdatar.observability.last_refresh_timestamp_seconds", lastRefresh, "Unix timestamp of the last successful business metric refresh");
    }

    private void gauge(String name, AtomicLong value, String description) {
        Gauge.builder(name, value, AtomicLong::doubleValue).description(description).register(registry);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${app.monitoring.business-refresh-ms:30000}")
    public void refresh() {
        try {
            OperationsHealthService.Health h = ops.health();
            var q = h.queues();
            databaseUp.set("UP".equals(h.database().status()) ? 1 : 0);
            redisUp.set("UP".equals(h.redis().status()) ? 1 : 0);
            databaseLatencyMs.set(h.database().latencyMs());
            redisLatencyMs.set(h.redis().latencyMs());
            workerEnabled.set(h.workerEnabled() ? 1 : 0);
            publishedEvents.set(h.publishedEvents());
            organizers.set(h.organizers());
            pendingPayments.set(q.pendingPayments());
            stalePayments.set(q.stalePayments());
            providerRecovery.set(q.providerOrderRecoveryPending());
            pendingRefunds.set(q.pendingRefunds());
            webhookBacklog.set(q.webhookBacklog());
            webhookStuck.set(q.webhookStuck());
            webhookDeadLetters.set(q.webhookDeadLetters());
            refundManualReview.set(q.refundManualReview());
            heldReservations.set(q.heldReservations());
            expiredReservations.set(q.expiredReservations());
            mailPending.set(q.mailPending());
            mailFailed.set(q.mailFailed());

            publishableEvents.set(scalar("select count(*) from events e where e.status in ('DRAFT','UNPUBLISHED') and e.starts_at > now() and exists (select 1 from ticket_types t where t.event_id=e.id)"));
            activeTicketTypes.set(scalar("select count(*) from ticket_types where status='ACTIVE'"));
            checkIns15m.set(scalar("select count(*) from tickets where checked_in_at >= now() - interval '15 minutes'"));
            orders5m.set(scalar("select count(*) from orders where created_at >= now() - interval '5 minutes'"));
            lastRefresh.set(System.currentTimeMillis() / 1000L);
        } catch (Exception ex) {
            refreshErrors.increment();
        }
    }

    private long scalar(String sql) {
        try {
            return ops.jdbcScalar(sql);
        } catch (Exception ex) {
            return 0L;
        }
    }
}
