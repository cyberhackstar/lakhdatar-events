package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Bounded lifecycle maintenance for non-ledger webhook payloads. Financial state remains in the core ledger. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
public class WebhookRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(WebhookRetentionJob.class);
    private final PaymentWebhookEventRepository events;
    private final DistributedLockService locks;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    @Value("${app.webhook.retention-days:365}") private int retentionDays;
    @Value("${app.webhook.retention-batch-size:500}") private int batchSize;

    @Scheduled(fixedDelayString="${app.webhook.retention-sweep:21600000}")
    void cleanup() {
        if (!workerEnabled) return;
        int safeDays = Math.max(30, retentionDays);
        int safeBatch = Math.max(50, Math.min(5000, batchSize));
        locks.withLock("job:webhook-retention", Duration.ofMinutes(5), () -> {
            int deleted = events.deleteTerminalOlderThan(Instant.now().minus(Duration.ofDays(safeDays)), safeBatch);
            if (deleted > 0) EnterpriseLog.info(log, "webhook.retention.cleaned", "event.category", "maintenance", "rows.deleted", deleted, "retention.days", safeDays);
        });
    }
}
