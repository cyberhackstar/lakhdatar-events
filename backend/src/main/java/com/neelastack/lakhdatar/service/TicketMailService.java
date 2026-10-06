package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.EventNotificationJob;
import com.neelastack.lakhdatar.domain.Order;
import com.neelastack.lakhdatar.domain.Ticket;
import com.neelastack.lakhdatar.domain.TicketMailJob;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrderRepository;
import com.neelastack.lakhdatar.repository.TicketMailJobRepository;
import com.neelastack.lakhdatar.repository.TicketRepository;
import com.neelastack.lakhdatar.security.UserPrincipal;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Emails the buyer / complimentary attendee their tickets (QR code inline + a link to the ticket page).
 *
 * Delivery is best-effort and NEVER part of the sale: a mail failure cannot undo a payment or an issued ticket.
 * The ticket page, the /recover flow and the "Email tickets again" action remain the source of truth.
 * Nothing sensitive is logged (no addresses, links or tokens).
 *
 * Paid-ticket delivery is backed by a small durable database queue. The bounded in-process worker is
 * only an accelerator; a restart cannot silently discard queued mail.
 */
@Service
public class TicketMailService {
    private static final Logger log = LoggerFactory.getLogger(TicketMailService.class);
    public static final String SENT = "SENT", NOT_CONFIGURED = "NOT_CONFIGURED", FAILED = "FAILED", NO_TICKETS = "NO_TICKETS";
    private static final int DEFAULT_WORKER_QUEUE = 100;
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(10);

    private final ObjectProvider<JavaMailSender> sender;
    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final EventRepository events;
    private final TicketMailJobRepository mailJobs;
    private final AccessTokenService accessTokens;
    private final QrCredentialService qr;
    private final EventAccessService eventAccess;
    private final RateLimitService rateLimits;
    private final TransactionTemplate tx;
    private final String from;
    private final String baseUrl;
    @Value("${app.worker.enabled:true}") private boolean workerEnabled;
    private final ExecutorService worker;

    public TicketMailService(ObjectProvider<JavaMailSender> sender, OrderRepository orders, TicketRepository tickets,
                             EventRepository events, TicketMailJobRepository mailJobs,
                             AccessTokenService accessTokens, QrCredentialService qr,
                             EventAccessService eventAccess, RateLimitService rateLimits,
                             TransactionTemplate tx,
                             @Value("${app.mail.from:}") String from,
                             @Value("${app.public-base-url:}") String baseUrl) {
        this.sender = sender; this.orders = orders; this.tickets = tickets; this.events = events; this.mailJobs = mailJobs;
        this.accessTokens = accessTokens; this.qr = qr; this.eventAccess = eventAccess; this.rateLimits = rateLimits; this.tx = tx;
        this.from = from == null ? "" : from.trim();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        int queueSize = DEFAULT_WORKER_QUEUE;
        this.worker = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                r -> { Thread t = new Thread(r, "ticket-mail"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean isConfigured() { return sender.getIfAvailable() != null && !from.isBlank() && !baseUrl.isBlank(); }

    /** Queues delivery for after the surrounding transaction commits (paid orders). Delivery work is durable. */
    public void sendAfterCommit(Long orderId) {
        if (!isConfigured()) return;
        Runnable queue = () -> {
            try {
                // This runs after the sale transaction has committed. A mail-queue DB failure must
                // never mark the payment/ticket transaction rollback-only. The repair sweep below
                // closes the small crash window between business commit and outbox persistence.
                enqueue(orderId);
                if (workerEnabled) submitOrder(orderId);
            } catch (Exception ex) {
                EnterpriseLog.warn(log, "mail.outbox.enqueue_failed", "event.category", "email", "order.id", orderId,
                        "error.type", ex.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { queue.run(); }
            });
        } else queue.run();
    }

    private void enqueue(Long orderId) {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            mailJobs.findByOrderId(orderId).ifPresentOrElse(job -> {
                if (job.getStatus() == TicketMailJob.Status.SENT) return;
                job.setStatus(TicketMailJob.Status.PENDING);
                job.setAttempts(0);
                job.setLastError(null);
                job.setSentAt(null);
                job.setNextAttemptAt(now);
                mailJobs.save(job);
            }, () -> {
                TicketMailJob job = new TicketMailJob();
                job.setOrderId(orderId);
                job.setStatus(TicketMailJob.Status.PENDING);
                job.setAttempts(0);
                job.setNextAttemptAt(now);
                mailJobs.save(job);
            });
        });
    }

    private void submitOrder(Long orderId) {
        mailJobs.findByOrderId(orderId).ifPresent(this::submitJob);
    }

    private void submitJob(TicketMailJob job) {
        try {
            worker.submit(() -> processJob(job.getId()));
        } catch (RejectedExecutionException ex) {
            // Durable PENDING state remains in PostgreSQL. The scheduled sweep will retry submission.
            EnterpriseLog.warn(log, "mail.delivery.queue_full", "event.category", "email", "mail.job_id", job.getId());
        }
    }

    private void processJob(Long jobId) {
        Long orderId = tx.execute(status -> mailJobs.findByIdForUpdate(jobId).map(job -> {
            Instant now = Instant.now();
            if (job.getStatus() == TicketMailJob.Status.SENT || job.getStatus() == TicketMailJob.Status.SKIPPED) return null;
            if (job.getStatus() == TicketMailJob.Status.FAILED && job.getAttempts() >= MAX_ATTEMPTS) return null;
            if (job.getStatus() == TicketMailJob.Status.PROCESSING && job.getNextAttemptAt().isAfter(now)) return null;
            job.setStatus(TicketMailJob.Status.PROCESSING);
            job.setAttempts(job.getAttempts() + 1);
            job.setNextAttemptAt(now.plus(PROCESSING_TIMEOUT));
            mailJobs.save(job);
            return job.getOrderId();
        }).orElse(null));
        if (orderId == null) return;

        String result = sendNow(orderId);
        tx.executeWithoutResult(status -> mailJobs.findByIdForUpdate(jobId).ifPresent(job -> {
            Instant now = Instant.now();
            if (SENT.equals(result)) {
                job.setStatus(TicketMailJob.Status.SENT);
                job.setSentAt(now);
                job.setLastError(null);
                job.setNextAttemptAt(now);
            } else if (NOT_CONFIGURED.equals(result) || NO_TICKETS.equals(result)) {
                job.setStatus(TicketMailJob.Status.SKIPPED);
                job.setLastError(result);
                job.setNextAttemptAt(now);
            } else {
                int attempts = job.getAttempts();
                if (attempts >= MAX_ATTEMPTS) {
                    job.setStatus(TicketMailJob.Status.FAILED);
                    job.setLastError("SMTP_SEND_FAILED_AFTER_MAX_ATTEMPTS");
                    job.setNextAttemptAt(now);
                    EnterpriseLog.error(log, "mail.delivery.exhausted", null, "event.category", "email",
                            "mail.job_id", jobId, "mail.delivery.max_attempts", MAX_ATTEMPTS);
                } else {
                    long delay = Math.min(Duration.ofHours(1).toMillis(), 5_000L * (1L << Math.min(8, Math.max(0, attempts - 1))));
                    job.setStatus(TicketMailJob.Status.PENDING);
                    job.setLastError("SMTP_SEND_FAILED");
                    job.setNextAttemptAt(now.plusMillis(delay));
                }
            }
            mailJobs.save(job);
        }));
    }

    @Scheduled(fixedDelayString = "${app.mail-delivery-repair-sweep:30000}")
    @ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
    public void repairMissingMailJobs() {
        if (!workerEnabled || !isConfigured()) return;
        Instant since = Instant.now().minus(Duration.ofDays(30));
        for (Order order : orders.findConfirmedPaidWithoutMailJob(Enums.OrderStatus.CONFIRMED, Enums.PaymentStatus.COMPLETED, since, PageRequest.of(0, 100))) {
            try {
                enqueue(order.getId());
                submitOrder(order.getId());
            } catch (Exception ex) {
                EnterpriseLog.warn(log, "mail.delivery.repair_deferred", "event.category", "email", "order.id", order.getId(),
                        "error.type", ex.getClass().getSimpleName());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.mail-delivery-cleanup-sweep:21600000}")
    @ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
    public void cleanupMailHistory() {
        if (!workerEnabled || !isConfigured()) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        long deleted = mailJobs.deleteByStatusInAndUpdatedAtBefore(
                List.of(TicketMailJob.Status.SENT, TicketMailJob.Status.SKIPPED, TicketMailJob.Status.FAILED), cutoff);
        if (deleted > 0) EnterpriseLog.info(log, "mail.history.cleaned", "event.category", "email", "mail.rows_deleted", deleted);
    }

    @Scheduled(fixedDelayString = "${app.mail-delivery-sweep:30000}")
    @ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
    public void sweepDurableMailQueue() {
        if (!workerEnabled || !isConfigured()) return;
        Instant now = Instant.now();
        List<TicketMailJob> due = mailJobs.findTop100ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                List.of(TicketMailJob.Status.PENDING, TicketMailJob.Status.PROCESSING), now);
        for (TicketMailJob job : due) submitJob(job);
    }

    @PreDestroy
    public void shutdown() {
        worker.shutdown();
    }

    /** Synchronous send used by complimentary issuance and the resend action. Never throws. */
    public String sendNow(Long orderId) {
        if (!isConfigured()) return NOT_CONFIGURED;
        try {
            Order o = orders.findById(orderId).orElse(null);
            if (o == null || o.getCustomerEmail() == null || o.getCustomerEmail().isBlank()) {
                EnterpriseLog.warn(log, "mail.delivery.skipped", "event.category", "email", "order.id", orderId, "mail.reason", "NO_RECIPIENT");
                return NO_TICKETS;
            }
            Event e = events.findById(o.getEventId()).orElse(null);
            List<Ticket> live = tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream()
                    .filter(t -> t.getStatus() == Enums.TicketStatus.ISSUED || t.getStatus() == Enums.TicketStatus.CHECKED_IN).toList();
            if (e == null || live.isEmpty()) {
                EnterpriseLog.warn(log, "mail.delivery.skipped", "event.category", "email", "order.id", orderId, "mail.reason", e == null ? "EVENT_NOT_FOUND" : "NO_LIVE_TICKETS");
                return NO_TICKETS;
            }

            JavaMailSender s = sender.getIfAvailable();
            MimeMessage msg = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from);
            h.setTo(o.getCustomerEmail());
            h.setSubject("Your ticket" + (live.size() > 1 ? "s" : "") + " for " + e.getName());

            StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1a151b\">")
                    .append("<h2 style=\"margin:0 0 8px\">").append(esc(e.getName())).append("</h2>")
                    .append("<p>Hi ").append(esc(o.getCustomerName())).append(", here ").append(live.size() > 1 ? "are your tickets" : "is your ticket")
                    .append(". There ").append(live.size() == 1 ? "is 1 seat" : "are ").append(live.size() == 1 ? "" : String.valueOf(live.size()) + " seats")
                    .append(" booked in this order. Show the QR code at the gate. Each code works for one entry.</p>");
            List<byte[]> images = new ArrayList<>();
            for (int i = 0; i < live.size(); i++) {
                Ticket t = live.get(i);
                String cid = "qr" + i;
                String uri = qr.pngDataUri(qr.credentialFor(t.getPublicId()));
                images.add(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)));
                String link = baseUrl + "/ticket/" + t.getPublicId() + "#access=" + URLEncoder.encode(accessTokens.issue(t.getPublicId()), StandardCharsets.UTF_8);
                html.append("<div style=\"border:1px solid #e5dfd7;border-radius:14px;padding:16px;margin:14px 0;text-align:center\">")
                        .append("<div style=\"font-weight:700\">Ticket ").append(esc(t.getTicketNumber())).append("</div>")
                        .append("<img src=\"cid:").append(cid).append("\" alt=\"Entry QR code\" width=\"220\" height=\"220\" style=\"margin:10px auto;display:block\"/>")
                        .append("<a href=\"").append(esc(link)).append("\">Open ticket page</a></div>");
            }
            html.append("<p style=\"font-size:12px;color:#6f6573\">Lost this email? Recover your tickets any time at ").append(esc(baseUrl)).append("/recover with your order number and email.</p></div>");
            h.setText(html.toString(), true);
            for (int i = 0; i < images.size(); i++) h.addInline("qr" + i, new ByteArrayResource(images.get(i)), "image/png");
            s.send(msg);
            EnterpriseLog.info(log, "mail.delivery.succeeded", "event.category", "email", "order.id", orderId, "ticket.count", live.size());
            return SENT;
        } catch (Exception ex) {
            EnterpriseLog.warn(log, "mail.delivery.failed", "event.category", "email", "order.id", orderId,
                    "error.type", ex.getClass().getSimpleName());
            return FAILED;
        }
    }

    /** Durable event-change mail used by the event-notification outbox. No ticket token is embedded. */
    public String sendEventNotification(EventNotificationJob job) {
        if (!isConfigured()) return NOT_CONFIGURED;
        try {
            Order o = orders.findById(job.getOrderId()).orElse(null);
            if (o == null || o.getEventId() == null || !o.getEventId().equals(job.getEventId())) {
                EnterpriseLog.warn(log, "event.notification.order-mismatch", "event.category", "email",
                        "event.notification.id", job.getId(), "order.id", job.getOrderId(), "event.id", job.getEventId());
                return NO_TICKETS;
            }
            Event e = events.findById(o.getEventId()).orElse(null);
            if (e == null || o.getCustomerEmail() == null || o.getCustomerEmail().isBlank()) return NO_TICKETS;

            JavaMailSender s = sender.getIfAvailable();
            MimeMessage msg = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(from);
            h.setTo(o.getCustomerEmail());

            boolean cancelled = job.getKind() == EventNotificationJob.Kind.CANCELLED;
            h.setSubject(cancelled ? "Important update: " + e.getName() + " has been cancelled" : "Important update: " + e.getName() + " has changed");

            java.time.ZoneId zone;
            try { zone = java.time.ZoneId.of(e.getTimezone()); } catch (Exception ignored) { zone = java.time.ZoneId.of("Asia/Kolkata"); }
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", java.util.Locale.ENGLISH).withZone(zone);
            String currentStart = e.getStartsAt() == null ? "Not available" : fmt.format(e.getStartsAt());
            String oldStart = job.getOldStartsAt() == null ? "Not available" : fmt.format(job.getOldStartsAt());
            String oldEnd = job.getOldEndsAt() == null ? "Not available" : fmt.format(job.getOldEndsAt());

            StringBuilder html = new StringBuilder("<div style=\"font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#1a151b\">")
                    .append("<h2 style=\"margin:0 0 12px\">").append(esc(e.getName())).append("</h2>");
            if (cancelled) {
                html.append("<p>Hi ").append(esc(o.getCustomerName())).append(", we’re sorry to inform you that this event has been cancelled.</p>")
                        .append("<p>Your order <strong>").append(esc(o.getOrderNumber())).append("</strong> is no longer valid for entry. Any eligible payment refund is handled by our recovery workflow; you do not need to make another payment or submit another order.</p>");
            } else {
                html.append("<p>Hi ").append(esc(o.getCustomerName())).append(", the details of this event have been updated.</p>")
                        .append("<p><strong>Current start:</strong> ").append(esc(currentStart)).append("</p>")
                        .append("<p>Your order <strong>").append(esc(o.getOrderNumber())).append("</strong> remains the source of truth. Please open your ticket or use the recovery page before travelling to confirm the latest venue and event details.</p>")
                        .append("<p style=\"font-size:12px;color:#6f6573\">Previous start: ").append(esc(oldStart)).append("; previous end: ").append(esc(oldEnd)).append(".</p>");
            }
            html.append("<p><a href=\"").append(esc(baseUrl)).append("/recover?order=").append(URLEncoder.encode(o.getOrderNumber(), StandardCharsets.UTF_8)).append("\">Open secure ticket recovery</a></p>")
                    .append("<p style=\"font-size:12px;color:#6f6573\">This message does not contain a ticket credential. Never share passwords or one-time codes by email.</p>")
                    .append("</div>");
            h.setText(html.toString(), true);
            s.send(msg);
            EnterpriseLog.info(log, "event.notification.email.sent", "event.category", "email", "event.notification.kind", job.getKind().name(), "order.id", job.getOrderId());
            return SENT;
        } catch (Exception ex) {
            EnterpriseLog.warn(log, "event.notification.email.failed", "event.category", "email", "event.notification.kind", job.getKind().name(), "order.id", job.getOrderId(), "error.type", ex.getClass().getSimpleName());
            return FAILED;
        }
    }

    /** "Email tickets again": allowed for ADMIN, the organizer's owner, or a manager assigned to the order's event. */
    public String resend(UUID orderPublicId, UserPrincipal actor) {
        Order o = orders.findByPublicId(orderPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
        if (!eventAccess.canManageEvent(o.getEventId(), actor.userId(), actor.role()))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not assigned to this event");
        if (!rateLimits.allow("ticket-resend:" + actor.userId(), 20, Duration.ofHours(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many email requests. Please try again later.");
        return sendNow(o.getId());
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
