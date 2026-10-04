-- v1.9.29: cursor-pagination index for event-scoped operational orders.
-- Ticket cursor APIs are indexed in V28 using the authoritative tickets.created_at column.
-- Keep this migration valid on a fresh database and on upgrades where V26 has not yet applied.
CREATE INDEX IF NOT EXISTS idx_orders_event_created_cursor
    ON orders(event_id, created_at DESC, id DESC);
