package com.neelastack.lakhdatar.security;

/** Authenticated identity plus proof that privileged MFA was completed for this session. */
public record UserPrincipal(Long userId, String email, String role, boolean mfaVerified) {
    /** Backwards-compatible constructor for non-MFA/internal principals. */
    public UserPrincipal(Long userId, String email, String role) {
        this(userId, email, role, false);
    }
}
