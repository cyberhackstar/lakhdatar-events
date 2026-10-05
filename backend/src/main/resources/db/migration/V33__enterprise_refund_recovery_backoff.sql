-- v2.0.10: durable refund retry scheduling and state-aware cancellation recovery.
ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE refunds
    ADD CONSTRAINT chk_refunds_next_attempt_after_created
    CHECK (next_attempt_at >= created_at) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_refunds_payment_status_next_attempt
    ON refunds(payment_id, status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_refunds_reason_status_next_attempt
    ON refunds(reason, status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_payments_status_created_event_recovery
    ON payments(status, created_at);

-- Existing rows are immediately eligible; future failures will set an explicit backoff time.
UPDATE refunds
   SET next_attempt_at = COALESCE(last_attempt_at, created_at)
 WHERE next_attempt_at IS NULL;

VALIDATE CONSTRAINT chk_refunds_next_attempt_after_created ON refunds;
