package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.MfaChallenge;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.MfaChallengeRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.client.j2se.MatrixToImageConfig;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enterprise TOTP MFA. Secrets are encrypted at rest and challenges are random, one-time and DB-backed,
 * so login works correctly across multiple application replicas.
 */
@Service
@RequiredArgsConstructor
public class MfaService {
    private static final Logger log = LoggerFactory.getLogger(MfaService.class);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final UserRepository users;
    private final MfaChallengeRepository challenges;
    private final RateLimitService rateLimits;
    private final AuditService audit;
    private final RefreshTokenRepository refreshTokens;

    @Value("${app.mfa.encryption-key:}") private String encryptionKey;
    @Value("${app.mfa.required-for-privileged:false}") private boolean requiredForPrivileged;
    @Value("${app.mfa.issuer:Neelastack Events}") private String issuer;
    @Value("${app.worker.enabled:true}") private boolean workerEnabled;

    public record Enrollment(String secret, String otpauthUri, String qrDataUri) {}
    public record Challenge(String token, boolean enrollment) {}

    public boolean requiredFor(Enums.UserRole role) {
        return requiredForPrivileged && switch (role) {
            case ADMIN, ORGANIZER, EVENT_MANAGER, FINANCE -> true;
            default -> false;
        };
    }

    @Transactional
    public Challenge createLoginChallenge(User user) {
        invalidateUserChallenges(user.getId());
        return createChallenge(user.getId(), MfaChallenge.Type.LOGIN);
    }

    @Transactional
    public Challenge createEnrollmentChallenge(User user) {
        invalidateUserChallenges(user.getId());
        return createChallenge(user.getId(), MfaChallenge.Type.ENROLLMENT);
    }

    @Transactional
    public Enrollment beginEnrollment(String challengeToken, String clientKey) {
        ensureConfigured();
        MfaChallenge c = lockedChallenge(challengeToken, clientKey, MfaChallenge.Type.ENROLLMENT);
        User u = users.findById(c.getUserId()).filter(User::isEnabled).orElseThrow(this::invalidChallenge);
        byte[] secret = new byte[20]; RANDOM.nextBytes(secret);
        String base32 = base32(secret);
        u.setMfaSecretEnc(encrypt(base32));
        users.save(u);
        String label = issuer + ":" + u.getEmail();
        String uri = "otpauth://totp/" + encode(label) + "?secret=" + base32 + "&issuer=" + encode(issuer) + "&algorithm=SHA1&digits=6&period=30";
        byte[] png = qrPng(uri);
        EnterpriseLog.info(log, "auth.mfa.enrollment.started", "event.category", "security", "user.id", u.getId());
        return new Enrollment(base32, uri, "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
    }

    @Transactional
    public User confirmEnrollment(String challengeToken, String code, String clientKey) {
        MfaChallenge c = lockedChallenge(challengeToken, clientKey, MfaChallenge.Type.ENROLLMENT);
        User u = users.findById(c.getUserId()).filter(User::isEnabled).orElseThrow(this::invalidChallenge);
        if (u.getMfaSecretEnc() == null) throw invalidChallenge();
        verifyCodeOrThrow(c, code, decrypt(u.getMfaSecretEnc()));
        u.setMfaEnabled(true);
        users.save(u);
        c.setUsedAt(Instant.now());
        challenges.save(c);
        EnterpriseLog.info(log, "auth.mfa.enrollment.completed", "event.category", "security", "user.id", u.getId());
        return u;
    }

    /** ADMIN-only emergency recovery for a privileged operator who has lost their MFA device. */
    @Transactional
    public void resetForAdmin(UUID targetPublicId, Long actorUserId) {
        User target = users.findByPublicId(targetPublicId).filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        if (target.getId().equals(actorUserId))
            throw new ApiException(HttpStatus.BAD_REQUEST, "MFA_SELF_RESET_FORBIDDEN", "Use a separate administrator account to reset MFA");
        target.setMfaEnabled(false);
        target.setMfaSecretEnc(null);
        Instant now = Instant.now();
        users.save(target);
        challenges.invalidateActiveByUserId(target.getId(), now);
        refreshTokens.revokeAllActiveByUserId(target.getId(), now);
        audit.log(actorUserId, "MFA_RESET_BY_ADMIN", "USER", target.getPublicId().toString(), null);
        EnterpriseLog.warn(log, "auth.mfa.admin_reset", "event.category", "security", "actor.user.id", actorUserId, "target.user.id", target.getId());
    }

    @Transactional
    public User verifyLogin(String challengeToken, String code, String clientKey) {
        MfaChallenge c = lockedChallenge(challengeToken, clientKey, MfaChallenge.Type.LOGIN);
        User u = users.findById(c.getUserId()).filter(User::isEnabled).orElseThrow(this::invalidChallenge);
        if (!u.isMfaEnabled() || u.getMfaSecretEnc() == null) throw invalidChallenge();
        verifyCodeOrThrow(c, code, decrypt(u.getMfaSecretEnc()));
        c.setUsedAt(Instant.now());
        challenges.save(c);
        EnterpriseLog.info(log, "auth.mfa.login.completed", "event.category", "security", "user.id", u.getId());
        return u;
    }

    private MfaChallenge lockedChallenge(String rawToken, String clientKey, MfaChallenge.Type expected) {
        if (rawToken == null || rawToken.length() < 40 || rawToken.length() > 256)
            throw invalidChallenge();
        if (!rateLimits.allow("mfa:" + hash(rawToken) + ":" + clientKey, 5, Duration.ofMinutes(10)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many authentication attempts");
        MfaChallenge c = challenges.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(this::invalidChallenge);
        Instant now = Instant.now();
        if (c.getType() != expected || c.getUsedAt() != null || !c.getExpiresAt().isAfter(now) || c.getAttempts() >= MAX_ATTEMPTS)
            throw invalidChallenge();
        return c;
    }

    private void verifyCodeOrThrow(MfaChallenge c, String code, String secret) {
        c.setAttempts(c.getAttempts() + 1);
        if (!validTotp(secret, code, Instant.now().getEpochSecond())) {
            if (c.getAttempts() >= MAX_ATTEMPTS) c.setUsedAt(Instant.now());
            challenges.save(c);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_MFA_CODE", "The verification code is invalid or has expired");
        }
    }

    private Challenge createChallenge(Long userId, MfaChallenge.Type type) {
        String raw = randomToken();
        MfaChallenge c = new MfaChallenge();
        c.setUserId(userId); c.setTokenHash(hash(raw)); c.setType(type); c.setExpiresAt(Instant.now().plus(CHALLENGE_TTL)); c.setAttempts(0);
        challenges.save(c);
        return new Challenge(raw, type == MfaChallenge.Type.ENROLLMENT);
    }

    private void invalidateUserChallenges(Long userId) { challenges.invalidateActiveByUserId(userId, Instant.now()); }

    private void ensureConfigured() {
        if (encryptionKey == null || encryptionKey.isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MFA_NOT_CONFIGURED", "MFA is not configured on this server");
        decodeEncryptionKey();
    }

    private byte[] decodeEncryptionKey() {
        try {
            byte[] b = Base64.getDecoder().decode(encryptionKey.trim());
            if (b.length == 32) return b;
        } catch (IllegalArgumentException ignored) { }
        String hex = encryptionKey.trim();
        if (hex.matches("(?i)[0-9a-f]{64}")) {
            byte[] b = new byte[32];
            for (int i = 0; i < 32; i++) b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            return b;
        }
        throw new IllegalStateException("MFA_ENCRYPTION_KEY must decode to exactly 32 bytes");
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(decodeEncryptionKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) { throw new IllegalStateException("Could not encrypt MFA secret", e); }
    }

    private String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length < 29) throw new IllegalArgumentException();
            byte[] iv = new byte[12]; System.arraycopy(combined, 0, iv, 0, 12);
            byte[] encrypted = new byte[combined.length - 12]; System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decodeEncryptionKey(), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Could not decrypt MFA secret", e); }
    }

    private boolean validTotp(String secret, String code, long epochSeconds) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long counter = epochSeconds / 30;
        for (long delta = -1; delta <= 1; delta++) {
            if (constantTimeEquals(totp(secret, counter + delta), code)) return true;
        }
        return false;
    }

    private String totp(String base32Secret, long counter) {
        try {
            byte[] key = base32Decode(base32Secret);
            byte[] text = new byte[8];
            for (int i = 7; i >= 0; i--) { text[i] = (byte) counter; counter >>>= 8; }
            var mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(text);
            int offset = hash[hash.length - 1] & 0x0f;
            int bin = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) { throw new IllegalStateException("Could not verify MFA code", e); }
    }

    private boolean constantTimeEquals(String a, String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII)); }

    private String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff); bits += 8;
            while (bits >= 5) { bits -= 5; out.append(BASE32[(buffer >>> bits) & 31]); }
        }
        if (bits > 0) out.append(BASE32[(buffer << (5 - bits)) & 31]);
        return out.toString();
    }

    private byte[] base32Decode(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            int val = base32Index(c); if (val < 0) throw new IllegalArgumentException("Bad base32");
            buffer = (buffer << 5) | val; bits += 5;
            if (bits >= 8) { bits -= 8; out.write((buffer >>> bits) & 0xff); }
        }
        return out.toByteArray();
    }

    private int base32Index(char c) {
        for (int i = 0; i < BASE32.length; i++) if (BASE32[i] == c) return i;
        return -1;
    }

    private byte[] qrPng(String data) {
        try {
            Map<EncodeHintType,Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 360, 360, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out, new MatrixToImageConfig());
            return out.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Could not generate MFA enrollment QR", e); }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.auth.mfa-cleanup-sweep:21600000}")
    public void cleanup() {
        if (!workerEnabled) return;
        distributedLocks.withLock("job:mfa-cleanup", Duration.ofMinutes(2), () -> {
            int deleted = challenges.deleteExpiredOrUsed(Instant.now().minus(Duration.ofDays(1)));
            if (deleted > 0) EnterpriseLog.info(log, "auth.mfa.cleaned", "event.category", "maintenance", "rows.deleted", deleted);
        });
    }

    private String encode(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20"); }
    private String randomToken() { byte[] b = new byte[48]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private ApiException invalidChallenge() { return new ApiException(HttpStatus.UNAUTHORIZED, "MFA_CHALLENGE_INVALID", "The MFA challenge is invalid or has expired"); }
    private String hash(String value) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String hex(byte[] b) { StringBuilder s = new StringBuilder(64); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
