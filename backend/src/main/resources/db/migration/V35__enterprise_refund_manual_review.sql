-- Terminal operator-review marker for refund recovery.
ALTER TABLE refunds
    ADD COLUMN IF NOT EXISTS manual_review_required BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_refunds_manual_review
    ON refunds (manual_review_required, status, next_attempt_at);
