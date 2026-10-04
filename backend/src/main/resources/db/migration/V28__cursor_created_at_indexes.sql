-- v1.9.30: align cursor pagination indexes with the actual created_at ordering predicates.
-- V26 retained useful issued_at indexes, while operational cursors paginate by created_at.
CREATE INDEX IF NOT EXISTS idx_tickets_event_created_cursor
    ON tickets(event_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_tickets_created_cursor
    ON tickets(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_events_starts_cursor
    ON events(starts_at DESC, id DESC);
