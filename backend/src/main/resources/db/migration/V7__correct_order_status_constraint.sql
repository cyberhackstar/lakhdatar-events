-- V6 used order status names from an earlier draft of the domain model.
-- Replace that constraint with the statuses implemented by the current application.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_status;
ALTER TABLE orders ADD CONSTRAINT chk_order_status CHECK (status IN ('CREATED','AWAITING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED'));
