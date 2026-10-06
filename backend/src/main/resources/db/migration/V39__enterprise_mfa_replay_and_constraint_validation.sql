ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_mfa_totp_counter BIGINT;

ALTER TABLE users
    ADD CONSTRAINT chk_users_last_mfa_totp_counter_non_negative
    CHECK (last_mfa_totp_counter IS NULL OR last_mfa_totp_counter >= 0);

-- V20 deliberately created this FK as NOT VALID so the historical migration remained compatible
-- with pre-existing data. Validate it now that the enterprise recovery path is in place.
ALTER TABLE ticket_reservations
    VALIDATE CONSTRAINT fk_ticket_reservations_order;
