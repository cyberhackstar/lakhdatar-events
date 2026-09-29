package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.RefreshToken;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import com.neelastack.lakhdatar.security.JwtService;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
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
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RateLimitService rateLimits;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public record AuthResult(String accessToken,String refreshToken,String role,String fullName) {}

    public AuthResult login(String email,String password,String clientKey){
        String normalizedEmail=email==null?"":email.trim().toLowerCase();
        java.time.Duration window=java.time.Duration.ofSeconds(props.rateLimit().windowSeconds());
        if(!rateLimits.allow("login-client:"+clientKey,props.rateLimit().loginPerWindow(),window)
                || !rateLimits.allow("login-email:"+hash(normalizedEmail),Math.max(3,props.rateLimit().loginPerWindow()/2),window))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many login attempts");
        User u=users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        // Always run one BCrypt comparison so response time does not reveal whether the account exists.
        boolean passwordOk=encoder.matches(password==null?"":password,u==null?timingEqualizerHash():u.getPasswordHash());
        if(u==null||!u.isEnabled()||!passwordOk)
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Invalid email or password");
        return issue(u);
    }

    // noRollbackFor is essential: the reuse branch revokes every active session and then throws.
    // Without it the RuntimeException rolls the revocation back and reuse detection is a no-op.
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String raw){
        if(raw==null||raw.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token invalid");
        RefreshToken current=refreshTokens.findByTokenHashForUpdate(hash(raw)).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token invalid"));
        if(current.getRevokedAt()!=null || !current.getExpiresAt().isAfter(Instant.now())) {
            // A revoked token that was rotated is a reuse signal. Revoke every remaining active
            // session for the user so a stolen refresh token cannot be used to keep minting sessions.
            if (current.getRevokedAt() != null && current.getReplacedByTokenHash() != null) {
                refreshTokens.revokeAllActiveByUserId(current.getUserId(), Instant.now());
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","Refresh token expired or revoked");
        }
        User u=users.findById(current.getUserId()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_REFRESH_TOKEN","User not found"));
        if(!u.isEnabled()) throw new ApiException(HttpStatus.UNAUTHORIZED,"ACCOUNT_DISABLED","Account disabled");
        AuthResult r=issue(u);
        current.setRevokedAt(Instant.now());
        current.setReplacedByTokenHash(hash(r.refreshToken()));
        refreshTokens.save(current);
        return r;
    }

    @Transactional
    public void revoke(String raw){
        if(raw==null||raw.isBlank()) return;
        refreshTokens.findByTokenHash(hash(raw)).ifPresent(token->{ token.setRevokedAt(Instant.now()); refreshTokens.save(token); });
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
