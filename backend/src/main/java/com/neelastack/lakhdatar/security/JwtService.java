package com.neelastack.lakhdatar.security;

import com.neelastack.lakhdatar.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final AppProperties props;
    public JwtService(AppProperties props){
        this.props=props;
        if(props.jwt()==null || props.jwt().secret()==null || props.jwt().secret().getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        if (props.jwt().issuer() == null || props.jwt().issuer().isBlank() || props.jwt().audience() == null || props.jwt().audience().isBlank())
            throw new IllegalStateException("JWT_ISSUER and JWT_AUDIENCE are required");
        this.key=Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }
    public String issueAccessToken(UserPrincipal principal){
        Instant now=Instant.now(), exp=now.plus(props.jwt().accessToken());
        return Jwts.builder().subject(String.valueOf(principal.userId())).issuer(props.jwt().issuer()).audience().add(props.jwt().audience()).and()
                .claim("email",principal.email()).claim("role",principal.role()).claim("mfa", principal.mfaVerified())
                .issuedAt(Date.from(now)).expiration(Date.from(exp)).signWith(key).compact();
    }
    public UserPrincipal parse(String token){
        try{
            Claims c=Jwts.parser().verifyWith(key).requireIssuer(props.jwt().issuer()).requireAudience(props.jwt().audience()).build().parseSignedClaims(token).getPayload();
            Boolean mfa = c.get("mfa", Boolean.class);
            return new UserPrincipal(Long.valueOf(c.getSubject()), c.get("email",String.class), c.get("role",String.class), Boolean.TRUE.equals(mfa));
        }catch(JwtException|IllegalArgumentException ex){return null;}
    }
}
