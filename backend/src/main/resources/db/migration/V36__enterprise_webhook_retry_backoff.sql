-- Enterprise webhook delivery recovery: durable backoff and terminal dead-letter state.
-- Validated payment webhooks are acknowledged only after durable persistence; processing is asynchronous.
ALTER TABLE payment_webhook_events
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dead_letter BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payment_webhook_events
    ADD CONSTRAINT chk_payment_webhook_dead_letter_requires_attempt
    CHECK ((dead_letter = FALSE) OR (attempt_count > 0));

CREATE INDEX IF NOT EXISTS idx_payment_webhook_due
    ON payment_webhook_events (processed, dead_letter, processing, next_attempt_at, received_at);
