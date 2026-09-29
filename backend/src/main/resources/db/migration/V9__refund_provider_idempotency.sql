ALTER TABLE refunds ADD COLUMN IF NOT EXISTS provider_receipt VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refunds_provider_receipt ON refunds(provider_receipt) WHERE provider_receipt IS NOT NULL;
