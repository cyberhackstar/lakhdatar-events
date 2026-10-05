-- Enterprise authentication recovery and privileged MFA.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS mfa_secret_enc VARCHAR(512);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_expiry
    ON password_reset_tokens (user_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expiry
    ON password_reset_tokens (expires_at);

CREATE TABLE IF NOT EXISTS mfa_challenges (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_mfa_challenge_type CHECK (type IN ('LOGIN', 'ENROLLMENT')),
    CONSTRAINT ck_mfa_challenge_attempts CHECK (attempts >= 0 AND attempts <= 10)
);

CREATE INDEX IF NOT EXISTS idx_mfa_challenges_user_expiry
    ON mfa_challenges (user_id, expires_at);

CREATE INDEX IF NOT EXISTS idx_mfa_challenges_expiry
    ON mfa_challenges (expires_at);
