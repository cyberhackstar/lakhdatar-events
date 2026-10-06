package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.domain.PasswordResetToken;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.PasswordResetTokenRepository;
import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Stateless, non-enumerating password recovery. Only a SHA-256 token hash is persisted. */
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final RateLimitService rateLimits;
    private final ObjectProvider<JavaMailSender> sender;
    private final AuditService audit;
    private final DistributedLockService distributedLocks;

    @Value("${app.mail.from:}") private String from;
    @Value("${app.public-base-url:}") private String baseUrl;
    @Value("${app.worker.enabled:true}") private boolean workerEnabled;

    private final SecureRandom random = new SecureRandom();
    /**
     * Password-reset mail is deliberately handed off after commit so SMTP latency never holds the
     * HTTP response thread. The queue is bounded so an SMTP outage cannot create unbounded memory
     * pressure. Rejected work is logged; the request itself remains non-enumerating and retryable.
     */
    private final ExecutorService mailExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            Thread.ofPlatform().daemon().name("password-reset-mail-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public record RequestResult(boolean accepted) {}

    /** Always returns the same logical success regardless of account existence or SMTP state. */
    @Transactional
    public RequestResult request(String email, String clientKey) {
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!rateLimits.allow("password-reset-client:" + clientKey, 8, Duration.ofMinutes(15))
                || !rateLimits.allow("password-reset-email:" + hash(normalized), 4, Duration.ofHours(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many password reset attempts");
        }
        if (normalized.length() > 255 || normalized.isBlank()) return new RequestResult(true);

        User u = users.findByEmailIgnoreCase(normalized).orElse(null);
        if (u == null || !u.isEnabled()) return new RequestResult(true);

        Instant now = Instant.now();
        tokens.invalidateUnusedByUserId(u.getId(), now);
        String raw = randomToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(u.getId());
        token.setTokenHash(hash(raw));
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokens.save(token);

        sendMailAfterCommit(u, raw, token.getExpiresAt());
        EnterpriseLog.info(log, "auth.password_reset.requested", "event.category", "authentication", "user.id", u.getId(), "mail.configured", mailEnabled());
        return new RequestResult(true);
    }

    @Transactional
    public void reset(String rawToken, String newPassword, String clientKey) {
        if (!rateLimits.allow("password-reset-submit-client:" + clientKey, 12, Duration.ofHours(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many password reset attempts");
        if (rawToken == null || rawToken.length() < 40 || rawToken.length() > 256 || newPassword == null
                || newPassword.length() < 12 || newPassword.length() > 128)
            throw invalid();

        PasswordResetToken token = tokens.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(this::invalid);
        Instant now = Instant.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) throw invalid();
        User u = users.findById(token.getUserId()).filter(User::isEnabled).orElseThrow(this::invalid);
        if (encoder.matches(newPassword, u.getPasswordHash()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSED", "Choose a password different from the current password");

        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
        token.setUsedAt(now);
        tokens.save(token);
        tokens.invalidateUnusedByUserId(u.getId(), now);
        refreshTokens.revokeAllActiveByUserId(u.getId(), now);
        audit.log(null, "PASSWORD_RESET_COMPLETED", "USER", u.getPublicId().toString(), null);
        EnterpriseLog.info(log, "auth.password_reset.completed", "event.category", "authentication", "user.id", u.getId());
    }

    @Scheduled(fixedDelayString = "${app.auth.password-reset-cleanup-sweep:21600000}")
    public void cleanup() {
        if (!workerEnabled) return;
        distributedLocks.withLock("job:password-reset-cleanup", Duration.ofMinutes(2), () -> {
            int deleted = tokens.deleteExpiredOrUsed(Instant.now().minus(Duration.ofDays(1)));
            if (deleted > 0) EnterpriseLog.info(log, "auth.password_reset.cleaned", "event.category", "maintenance", "rows.deleted", deleted);
        });
    }

    private boolean mailEnabled() {
        return sender.getIfAvailable() != null && from != null && !from.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    private void sendMailAfterCommit(User u, String raw, Instant expiresAt) {
        Runnable send = () -> {
            try { mailExecutor.submit(() -> sendMail(u, raw, expiresAt)); }
            catch (RejectedExecutionException ex) {
                EnterpriseLog.warn(log, "auth.password_reset.mail_queue_rejected", "event.category", "email", "error.type", ex.getClass().getSimpleName());
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }

    @jakarta.annotation.PreDestroy
    public void shutdownMailExecutor() {
        mailExecutor.shutdown();
    }

    private void sendMail(User u, String raw, Instant expiresAt) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null || from == null || from.isBlank() || baseUrl == null || baseUrl.isBlank()) return;
        try {
            String cleanBase = baseUrl.trim().replaceAll("/+$", "");
            String link = cleanBase + "/reset-password#token=" + java.net.URLEncoder.encode(raw, StandardCharsets.UTF_8);
            MimeMessage message = s.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from.trim());
            helper.setTo(u.getEmail());
            helper.setSubject("Reset your Neelastack Events password");
            helper.setText("Hi " + safe(u.getFullName()) + ",\n\n"
                    + "A password reset was requested for your Neelastack Events account.\n\n"
                    + link + "\n\n"
                    + "This one-time link expires at " + expiresAt + ". If you did not request it, you can ignore this email.\n");
            s.send(message);
        } catch (Exception ex) {
            // Recovery requests remain non-enumerating and retryable; a user can request another link.
            EnterpriseLog.warn(log, "auth.password_reset.mail_failed", "event.category", "email", "error.type", ex.getClass().getSimpleName());
        }
    }

    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "This reset link is invalid or has expired"); }
    private String randomToken() { byte[] bytes = new byte[48]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String safe(String s) { return s == null ? "there" : s.replace("\r", "").replace("\n", ""); }
    private String hash(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String hex(byte[] b) { StringBuilder s = new StringBuilder(64); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
