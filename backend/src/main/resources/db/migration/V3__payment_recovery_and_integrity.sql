-- Payment/provider recovery metadata and defensive data constraints.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_order_state VARCHAR(24) NOT NULL DEFAULT 'NOT_CREATED';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS razorpay_order_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_last_error VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_payments_provider_order_state ON payments (razorpay_order_state, status, created_at);

ALTER TABLE refunds ADD COLUMN IF NOT EXISTS provider_status VARCHAR(32);
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS last_error VARCHAR(255);
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_refunds_status_created ON refunds (status, created_at);

-- Normalize legacy queued refunds before recovery workers start.
UPDATE refunds SET status = 'PROCESSING' WHERE status = 'REQUESTED';

ALTER TABLE events ADD CONSTRAINT chk_event_capacity_positive CHECK (capacity IS NULL OR capacity > 0);
ALTER TABLE ticket_types ADD CONSTRAINT chk_ticket_price_positive CHECK (price_minor_units > 0);
ALTER TABLE ticket_types ADD CONSTRAINT chk_ticket_bounds CHECK (min_per_order > 0 AND max_per_order >= min_per_order);
ALTER TABLE tickets ADD CONSTRAINT chk_ticket_number_nonblank CHECK (length(trim(ticket_number)) > 0);
