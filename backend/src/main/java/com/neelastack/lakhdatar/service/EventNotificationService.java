package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.domain.EventNotificationJob;
import com.neelastack.lakhdatar.repository.EventNotificationJobRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EventNotificationService {
    private static final Logger log = LoggerFactory.getLogger(EventNotificationService.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    private final EventNotificationJobRepository jobs;
    private final DistributedLockService locks;
    private final TicketMailService mail;
    private final TransactionTemplate tx;

    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    private final ExecutorService worker = new ThreadPoolExecutor(
            2, 4, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(500),
            r -> { Thread t = new Thread(r, "event-notification-mail"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());

    /** Durable outbox insertion; called from the same business transaction as the event mutation. */
    public int queueCancellation(Long eventId) {
        return jobs.enqueueForEvent(eventId, EventNotificationJob.Kind.CANCELLED.name(), "CANCELLED", null, null);
    }

    /** Durable outbox insertion for a buyer-visible event change. */
    public int queueDetailsChange(Long eventId, String changeKey, Instant oldStartsAt, Instant oldEndsAt) {
        return jobs.enqueueForEvent(eventId, EventNotificationJob.Kind.DETAILS_CHANGED.name(), changeKey, oldStartsAt, oldEndsAt);
    }

    @Scheduled(fixedDelayString = "${app.event-notification-sweep:15000}")
    @ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    void sweep() {
        if (!workerEnabled || !mail.isConfigured()) return;
        locks.withLock("job:event-notifications", Duration.ofMinutes(2), () -> {
            Instant now = Instant.now();
            List<EventNotificationJob> due = jobs.findTop500ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                    List.of(EventNotificationJob.Status.PENDING, EventNotificationJob.Status.PROCESSING), now);
            for (EventNotificationJob job : due) submit(job.getId());
        });
    }

    private void submit(Long jobId) {
        try {
            worker.execute(() -> process(jobId));
        } catch (RejectedExecutionException ex) {
            EnterpriseLog.warn(log, "event.notification.queue_full", "event.category", "email", "event.notification.job_id", jobId);
        }
    }

    private void process(Long jobId) {
        EventNotificationJob claimed = tx.execute(status -> jobs.findByIdForUpdate(jobId).map(job -> {
            Instant now = Instant.now();
            if (job.getStatus() == EventNotificationJob.Status.SENT || job.getStatus() == EventNotificationJob.Status.SKIPPED) return null;
            if (job.getStatus() == EventNotificationJob.Status.FAILED && job.getAttempts() >= MAX_ATTEMPTS) return null;
            if (job.getStatus() == EventNotificationJob.Status.PROCESSING && job.getNextAttemptAt().isAfter(now)) return null;
            job.setStatus(EventNotificationJob.Status.PROCESSING);
            job.setAttempts(job.getAttempts() + 1);
            job.setNextAttemptAt(now.plus(PROCESSING_TIMEOUT));
            jobs.save(job);
            return job;
        }).orElse(null));
        if (claimed == null) return;

        String result = mail.sendEventNotification(claimed);
        tx.executeWithoutResult(status -> jobs.findByIdForUpdate(jobId).ifPresent(job -> {
            Instant now = Instant.now();
            if (TicketMailService.SENT.equals(result)) {
                job.setStatus(EventNotificationJob.Status.SENT);
                job.setSentAt(now);
                job.setLastError(null);
                job.setNextAttemptAt(now);
            } else if (TicketMailService.NOT_CONFIGURED.equals(result) || TicketMailService.NO_TICKETS.equals(result)) {
                job.setStatus(EventNotificationJob.Status.SKIPPED);
                job.setLastError(result);
                job.setNextAttemptAt(now);
            } else {
                int attempts = job.getAttempts();
                if (attempts >= MAX_ATTEMPTS) {
                    job.setStatus(EventNotificationJob.Status.FAILED);
                    job.setLastError("SMTP_SEND_FAILED_AFTER_MAX_ATTEMPTS");
                    job.setNextAttemptAt(now);
                    EnterpriseLog.error(log, "event.notification.exhausted", null, "event.category", "email", "event.notification.job_id", jobId, "max_attempts", MAX_ATTEMPTS);
                } else {
                    long delay = Math.min(Duration.ofHours(1).toMillis(), 5_000L * (1L << Math.min(8, Math.max(0, attempts - 1))));
                    job.setStatus(EventNotificationJob.Status.PENDING);
                    job.setLastError("SMTP_SEND_FAILED");
                    job.setNextAttemptAt(now.plusMillis(delay));
                }
            }
            jobs.save(job);
        }));
    }

    @Scheduled(fixedDelayString = "${app.event-notification-cleanup-sweep:21600000}")
    @ConditionalOnProperty(prefix = "app.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    void cleanup() {
        if (!workerEnabled) return;
        long deleted = jobs.deleteHistory(List.of(EventNotificationJob.Status.SENT, EventNotificationJob.Status.SKIPPED), Instant.now().minus(Duration.ofDays(90)));
        if (deleted > 0) EnterpriseLog.info(log, "event.notification.history.cleaned", "event.category", "email", "rows_deleted", deleted);
    }

    @PreDestroy
    public void shutdown() { worker.shutdown(); }
}
