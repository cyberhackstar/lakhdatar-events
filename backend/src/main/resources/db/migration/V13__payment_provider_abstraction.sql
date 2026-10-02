-- V13: provider-neutral payment routing while preserving legacy Razorpay columns for compatibility.
ALTER TABLE events ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(24) NOT NULL DEFAULT 'RAZORPAY';
ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_payment_provider;
ALTER TABLE events ADD CONSTRAINT chk_events_payment_provider CHECK (payment_provider IN ('RAZORPAY','CASHFREE'));

ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider VARCHAR(24) NOT NULL DEFAULT 'RAZORPAY';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_signature TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_public_key VARCHAR(255);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS provider_session_id VARCHAR(500);
ALTER TABLE payments DROP CONSTRAINT IF EXISTS chk_payments_provider;
ALTER TABLE payments ADD CONSTRAINT chk_payments_provider CHECK (provider IN ('RAZORPAY','CASHFREE'));
CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_order_id ON payments(provider_order_id) WHERE provider_order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_provider_payment_id ON payments(provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_provider_status ON payments(provider,status,created_at);
UPDATE payments SET provider_order_id=razorpay_order_id WHERE provider_order_id IS NULL AND razorpay_order_id IS NOT NULL;
UPDATE payments SET provider_payment_id=razorpay_payment_id WHERE provider_payment_id IS NULL AND razorpay_payment_id IS NOT NULL;
