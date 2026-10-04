-- v1.9.29: optimize platform-wide immutable ledger pagination.
CREATE INDEX IF NOT EXISTS idx_financial_ledger_created
    ON financial_ledger_entries(created_at DESC, id DESC);
