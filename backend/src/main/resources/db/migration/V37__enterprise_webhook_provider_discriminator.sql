-- Webhook recovery is shared across payment providers, so provider identity must be durable.
ALTER TABLE payment_webhook_events
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20);

UPDATE payment_webhook_events
   SET provider = CASE
       WHEN provider_event_id LIKE 'cashfree:%' THEN 'CASHFREE'
       ELSE 'RAZORPAY'
   END
 WHERE provider IS NULL;

ALTER TABLE payment_webhook_events
    ALTER COLUMN provider SET NOT NULL;

ALTER TABLE payment_webhook_events
    ADD CONSTRAINT chk_payment_webhook_provider
    CHECK (provider IN ('RAZORPAY', 'CASHFREE'));

CREATE INDEX IF NOT EXISTS idx_payment_webhook_provider_due
    ON payment_webhook_events (provider, processed, dead_letter, processing, next_attempt_at, received_at);
