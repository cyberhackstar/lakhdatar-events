ALTER TABLE payment_webhook_events ADD COLUMN IF NOT EXISTS processing BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payment_webhook_events ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;
ALTER TABLE payment_webhook_events ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payment_webhook_events ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);
DROP INDEX IF EXISTS uq_payment_webhook_payload_hash;
CREATE INDEX IF NOT EXISTS idx_payment_webhook_payload_hash ON payment_webhook_events(payload_hash);
CREATE INDEX IF NOT EXISTS idx_payment_webhook_processing ON payment_webhook_events(processed, processing, received_at);
ALTER TABLE payment_webhook_events ADD CONSTRAINT chk_payment_webhook_attempt_count_non_negative CHECK (attempt_count >= 0);
