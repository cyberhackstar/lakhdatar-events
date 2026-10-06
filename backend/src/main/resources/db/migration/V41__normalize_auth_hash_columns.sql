-- Normalize fixed-width auth token hashes to the VARCHAR(64) contract used by JPA.
-- RTRIM removes CHAR(64) padding while preserving the actual SHA-256 hex digest.
ALTER TABLE password_reset_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64)
    USING RTRIM(token_hash);

ALTER TABLE mfa_challenges
    ALTER COLUMN token_hash TYPE VARCHAR(64)
    USING RTRIM(token_hash);
