-- Enterprise security and financial reconciliation hardening.
-- Forward-only migration; prior migrations remain immutable.

ALTER TABLE orders ADD COLUMN IF NOT EXISTS checkout_session_hash VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_orders_checkout_session_hash ON orders(checkout_session_hash) WHERE checkout_session_hash IS NOT NULL;

ALTER TABLE refunds ADD COLUMN IF NOT EXISTS provider_refund_id VARCHAR(100);
UPDATE refunds SET provider_refund_id = razorpay_refund_id
WHERE provider_refund_id IS NULL AND razorpay_refund_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_refunds_provider_refund_id ON refunds(provider_refund_id) WHERE provider_refund_id IS NOT NULL;

-- Gate-bound staff may be stored with a NULL gate only for legacy rows; application authorization is strict.
ALTER TABLE event_staff DROP CONSTRAINT IF EXISTS chk_event_staff_gate_non_blank;
ALTER TABLE event_staff ADD CONSTRAINT chk_event_staff_gate_non_blank
    CHECK (gate IS NULL OR length(trim(gate)) > 0);
