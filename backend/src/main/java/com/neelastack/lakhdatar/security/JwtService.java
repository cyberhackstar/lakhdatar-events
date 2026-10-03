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
        this.key=Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }
    public String issueAccessToken(UserPrincipal principal){
        Instant now=Instant.now(), exp=now.plus(props.jwt().accessToken());
        return Jwts.builder().subject(String.valueOf(principal.userId())).claim("email",principal.email()).claim("role",principal.role())
                .issuedAt(Date.from(now)).expiration(Date.from(exp)).signWith(key).compact();
    }
    public UserPrincipal parse(String token){
        try{
            Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new UserPrincipal(Long.valueOf(c.getSubject()), c.get("email",String.class), c.get("role",String.class));
        }catch(JwtException|IllegalArgumentException ex){return null;}
    }
}
