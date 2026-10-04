package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One-time set-password invite. Only the SHA-256 of the emailed token is persisted. */
@Entity
@Table(name = "user_invites")
@Getter @Setter
public class UserInvite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", updatable = false) private Instant createdAt = Instant.now();
}
