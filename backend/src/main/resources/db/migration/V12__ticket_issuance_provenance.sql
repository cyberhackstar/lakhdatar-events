-- V12: ticket provenance for complimentary/manager-issued tickets.
ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS source VARCHAR(32) NOT NULL DEFAULT 'ONLINE_PAYMENT',
    ADD COLUMN IF NOT EXISTS issued_by_user_id BIGINT REFERENCES users(id);

ALTER TABLE tickets DROP CONSTRAINT IF EXISTS chk_ticket_source;
ALTER TABLE tickets ADD CONSTRAINT chk_ticket_source
    CHECK (source IN ('ONLINE_PAYMENT','COMPLIMENTARY_MANAGER'));

CREATE INDEX IF NOT EXISTS idx_tickets_issued_by
    ON tickets (issued_by_user_id)
    WHERE issued_by_user_id IS NOT NULL;

-- A manager-issued ticket must have an issuer; normal online tickets must not.
ALTER TABLE tickets DROP CONSTRAINT IF EXISTS chk_ticket_issuer_consistency;
ALTER TABLE tickets ADD CONSTRAINT chk_ticket_issuer_consistency
    CHECK ((source = 'COMPLIMENTARY_MANAGER' AND issued_by_user_id IS NOT NULL)
        OR (source = 'ONLINE_PAYMENT' AND issued_by_user_id IS NULL));
