-- v1.9.52: model multiple provider refunds per payment safely.
-- A single payment may receive multiple partial refunds from a provider dashboard or API.

DROP INDEX IF EXISTS uq_refunds_payment;
DROP INDEX IF EXISTS uq_refunds_provider_receipt;

CREATE INDEX IF NOT EXISTS idx_refunds_payment_created
    ON refunds(payment_id, created_at ASC, id ASC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refunds_provider_refund_id
    ON refunds(provider_refund_id) WHERE provider_refund_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_refunds_provider_receipt
    ON refunds(provider_receipt) WHERE provider_receipt IS NOT NULL;
