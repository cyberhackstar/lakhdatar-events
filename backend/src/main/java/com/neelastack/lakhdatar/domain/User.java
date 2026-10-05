package com.neelastack.lakhdatar.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="users")
@Getter @Setter
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="public_id", nullable=false, unique=true, updatable=false) private UUID publicId=UUID.randomUUID();
    @Column(nullable=false, unique=true, length=255) private String email;
    @Column(length=32) private String phone;
    @Column(name="password_hash", nullable=false, length=255) private String passwordHash;
    @Column(name="full_name", nullable=false, length=255) private String fullName;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private Enums.UserRole role;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="must_change_password", nullable=false) private boolean mustChangePassword=false;
    @Column(name="mfa_enabled", nullable=false) private boolean mfaEnabled=false;
    @Column(name="mfa_secret_enc", length=512) private String mfaSecretEnc;
    @Column(name="created_at", updatable=false) private Instant createdAt=Instant.now();
    @Column(name="updated_at") private Instant updatedAt=Instant.now();
    @PreUpdate void touch(){updatedAt=Instant.now();}
}
