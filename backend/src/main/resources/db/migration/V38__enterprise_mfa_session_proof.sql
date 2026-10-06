-- Enterprise MFA session proof. Refresh tokens minted after successful privileged MFA carry
-- a durable proof bit so refresh cannot silently elevate an unverified legacy session.
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS mfa_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_mfa
    ON refresh_tokens (user_id, mfa_verified, expires_at);
