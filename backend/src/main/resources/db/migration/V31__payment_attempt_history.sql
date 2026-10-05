-- v1.9.50: persist every provider transaction reference seen for a local payment.
-- A single checkout can create multiple provider payment attempts; the main payments row
-- intentionally retains only the authoritative successful provider payment ID.
CREATE TABLE IF NOT EXISTS payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    provider VARCHAR(24) NOT NULL,
    provider_payment_id VARCHAR(255) NOT NULL,
    provider_order_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    provider_message VARCHAR(500),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_payment_attempts_provider_payment_id UNIQUE (provider_payment_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_payment_id
    ON payment_attempts(payment_id);

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_order_id
    ON payment_attempts(provider_order_id);

