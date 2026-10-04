-- v1.9.24: let payment reconciliation sweeps rotate through candidates instead of
-- re-reading the same oldest rows forever. Forward-only; prior migrations stay immutable.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS last_reconciled_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payments_reconcile_rotation
    ON payments(status, last_reconciled_at NULLS FIRST, created_at);
