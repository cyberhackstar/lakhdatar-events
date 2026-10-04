-- v1.9.29: index physical ordering used by cursor-paginated operational APIs.
CREATE INDEX IF NOT EXISTS idx_tickets_event_issued_cursor
    ON tickets(event_id, issued_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_issued_cursor
    ON tickets(issued_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_orders_event_created_cursor
    ON orders(event_id, created_at DESC, id DESC);
