-- Indexes supporting the durable mail-repair sweep and recovery queries introduced in v1.9.23.
CREATE INDEX IF NOT EXISTS idx_orders_status_created_at
    ON orders(status, created_at, id);

CREATE INDEX IF NOT EXISTS idx_payments_status_order
    ON payments(status, order_id);
