package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.RefreshToken;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.domain.UserInvite;
import com.neelastack.lakhdatar.repository.UserInviteRepository;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import com.neelastack.lakhdatar.security.JwtService;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final UserInviteRepository invites;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimitService rateLimits;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();
    // A rotated refresh token that is presented again within this window is treated as a lost
    // response (flaky network), not theft: the request is rejected but other sessions survive.
    @org.springframework.beans.factory.annotation.Value("${app.security.refresh-reuse-grace-seconds:30}") private long refreshReuseGraceSeconds = 30;

    public record AuthResult(String accessToken,String refreshToken,String role,String fullName) {}

    public AuthResult login(String email,String password,String clientKey){
        EnterpriseLog.debug(log, "auth.login.started", "event.category", "authentication");
        String normalizedEmail=email==null?"":email.trim().toLowerCase();
        java.time.Duration window=java.time.Duration.ofSeconds(props.rateLimit().windowSeconds());
        if(!rateLimits.allow("login-client:"+clientKey,props.rateLimit().loginPerWindow(),window)
                || !rateLimits.allow("login-email:"+hash(normalizedEmail),Math.max(3,props.rateLimit().loginPerWindow()/2),window)) {
            EnterpriseLog.warn(log, "auth.login.rate_limited", "event.category", "authentication", "error.code", "RATE_LIMITED");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many login attempts");
        }
        User u=users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        // Always run one BCrypt comparison so response time does not reveal whether the account exists.
        boolean passwordOk=encoder.matches(password==null?"":password,u==null?timingEqualizerHash():u.getPasswordHash());
        if(u==null||!u.isEnabled()||!passwordOk){
            EnterpriseLog.warn(log, "auth.login.failed", "event.category", "authentication", "error.code", "INVALID_CREDENTIALS");
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid email or password");
        }
        EnterpriseLog.info(log, "auth.login.succeeded", "event.category", "authentication", "user.id", u.getId(), "user.role", u.getRole().name());
        return issue(u);
    }

    // noRollbackFor is essential: the reuse branch revokes every active session and then throws.
    // Without it the RuntimeException rolls the revocation back and reuse detection is a no-op.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String raw){
        EnterpriseLog.debug(log, "auth.refresh.started", "event.category", "authentication");
        if(raw==null||raw.isBlank()){ EnterpriseLog.warn(log, "auth.refresh.rejected", "event.category", "authentication", "error.code", "INVALID_REFRESH_TOKEN"); throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token invalid"); }
        RefreshToken current=refreshTokens.findByTokenHashForUpdate(hash(raw)).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token invalid"));
        if(current.getRevokedAt()!=null || !current.getExpiresAt().isAfter(Instant.now())) {
            // A revoked token that was rotated is a reuse signal. Revoke every remaining active
            // session for the user so a stolen refresh token cannot be used to keep minting sessions.
            if (current.getRevokedAt() != null && current.getReplacedByTokenHash() != null) {
                boolean withinGrace = refreshReuseGraceSeconds > 0
                        && !current.getRevokedAt().plusSeconds(refreshReuseGraceSeconds).isBefore(Instant.now());
                EnterpriseLog.warn(log, "auth.refresh.reuse_detected", "event.category", "security", "user.id", current.getUserId(),
                        "security.reason", withinGrace ? "ROTATED_TOKEN_REPLAY_WITHIN_GRACE" : "ROTATED_TOKEN_REPLAY");
                if (!withinGrace) {
                    refreshTokens.revokeAllActiveByUserId(current.getUserId(), Instant.now());
                    EnterpriseLog.error(log, "auth.refresh.sessions_revoked", null, "event.category", "security",
                            "user.id", current.getUserId(), "security.reason", "REFRESH_TOKEN_REUSE");
                }
            }
            EnterpriseLog.warn(log, "auth.refresh.rejected", "event.category", "authentication", "error.code", "INVALID_REFRESH_TOKEN", "security.reason", current.getRevokedAt()!=null ? "REVOKED" : "EXPIRED");
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token expired or revoked");
        }
        User u=users.findById(current.getUserId()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","User not found"));
        if(!u.isEnabled()) {
            EnterpriseLog.warn(log, "auth.refresh.rejected", "event.category", "authentication", "error.code", "ACCOUNT_DISABLED", "user.id", u.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED,"ACCOUNT_DISABLED","Account disabled");
        }
        AuthResult r=issue(u);
        current.setRevokedAt(Instant.now());
        current.setReplacedByTokenHash(hash(r.refreshToken()));
        refreshTokens.save(current);
        EnterpriseLog.info(log, "auth.refresh.succeeded", "event.category", "authentication", "user.id", u.getId(), "user.role", u.getRole().name());
        return r;
    }


    // ---- invites and forced password change -------------------------------------------------------------

    /** Single-use, 48h set-password link. Every failure mode returns the same error so tokens cannot be probed. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult acceptInvite(String token, String newPassword, String clientKey){
        java.time.Duration window=java.time.Duration.ofSeconds(props.rateLimit().windowSeconds());
        if(!rateLimits.allow("invite-client:"+clientKey,Math.max(5,props.rateLimit().loginPerWindow()),window))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many attempts");
        ApiException invalid=new ApiException(HttpStatus.BAD_REQUEST,"INVALID_INVITE","This invite link is invalid or has expired");
        if(token==null||token.isBlank()||newPassword==null||newPassword.length()<12||newPassword.length()>128) throw invalid;
        UserInvite inv=invites.findByTokenHashForUpdate(TeamService.sha256(token)).orElseThrow(()->invalid);
        Instant now=Instant.now();
        if(inv.getUsedAt()!=null||!inv.getExpiresAt().isAfter(now)) throw invalid;
        User u=users.findById(inv.getUserId()).filter(User::isEnabled).orElseThrow(()->invalid);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        users.save(u);
        inv.setUsedAt(now);
        invites.save(inv);
        refreshTokens.revokeAllActiveByUserId(u.getId(),now);
        EnterpriseLog.info(log, "auth.invite.accepted", "event.category", "authentication", "user.id", u.getId(), "user.role", u.getRole().name());
        return issue(u);
    }

    @Transactional
    public AuthResult changePassword(Long userId,String current,String next){
        java.time.Duration window=java.time.Duration.ofSeconds(props.rateLimit().windowSeconds());
        if(!rateLimits.allow("pwchange:"+userId,5,window))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many attempts");
        User u=users.findById(userId).filter(User::isEnabled).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Authentication required"));
        if(!encoder.matches(current==null?"":current,u.getPasswordHash()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURRENT_PASSWORD","Current password is incorrect");
        if(next==null||next.length()<12||next.length()>128)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PASSWORD","New password must be between 12 and 128 characters");
        if(next.equals(current)) throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_REUSED","Choose a password different from the current one");
        u.setPasswordHash(encoder.encode(next));
        u.setMustChangePassword(false);
        users.save(u);
        refreshTokens.revokeAllActiveByUserId(u.getId(),Instant.now());
        EnterpriseLog.info(log, "auth.password.changed", "event.category", "authentication", "user.id", u.getId(), "user.role", u.getRole().name());
        return issue(u);
    }

    @Transactional(readOnly = true)
    public boolean mustChangePassword(Long userId){
        return users.findById(userId).map(User::isMustChangePassword).orElse(false);
    }

    @Transactional
    public void revoke(String raw){
        if(raw==null||raw.isBlank()) return;
        refreshTokens.findByTokenHash(hash(raw)).ifPresent(token->{ token.setRevokedAt(Instant.now()); refreshTokens.save(token); EnterpriseLog.info(log, "auth.logout.succeeded", "event.category", "authentication", "user.id", token.getUserId()); });
    }

    private volatile String timingEqualizerHash;
    private String timingEqualizerHash(){
        String h=timingEqualizerHash;
        if(h==null){ h=encoder.encode("timing-equalizer-"+UUID.randomUUID()); timingEqualizerHash=h; }
        return h;
    }

    private AuthResult issue(User u){
        UserPrincipal p=new UserPrincipal(u.getId(),u.getEmail(),u.getRole().name());
        String access=jwt.issueAccessToken(p);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(48));
        RefreshToken rt=new RefreshToken(); rt.setUserId(u.getId()); rt.setTokenHash(hash(raw)); rt.setExpiresAt(Instant.now().plus(props.jwt().refreshToken()));
        refreshTokens.save(rt);
        return new AuthResult(access,raw,u.getRole().name(),u.getFullName());
    }
    private byte[] randomBytes(int n){byte[] b=new byte[n];random.nextBytes(b);return b;}
    private String hash(String value){try{return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String hex(byte[] b){StringBuilder s=new StringBuilder(b.length*2);for(byte x:b)s.append(String.format("%02x",x));return s.toString();}
}
