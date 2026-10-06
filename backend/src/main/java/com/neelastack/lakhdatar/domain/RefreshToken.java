package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant;
@Entity @Table(name="refresh_tokens") @Getter @Setter
public class RefreshToken {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 @Column(name="revoked_at") private Instant revokedAt;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 @Column(name="replaced_by_token_hash") private String replacedByTokenHash;
 @Column(name="mfa_verified", nullable=false) private boolean mfaVerified = false;
}
