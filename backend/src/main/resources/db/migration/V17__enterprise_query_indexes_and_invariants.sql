-- Enterprise query-performance indexes and final clean-setup invariants.
-- Forward-only; no prior migration is modified.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_events_status_starts_at
    ON events(status, starts_at);
CREATE INDEX IF NOT EXISTS idx_events_slug_status
    ON events(slug, status);
CREATE INDEX IF NOT EXISTS idx_orders_event_status_created
    ON orders(event_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_payments_provider_order_status
    ON payments(provider, provider_order_id, status)
    WHERE provider_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tickets_event_status
    ON tickets(event_id, status);
CREATE INDEX IF NOT EXISTS idx_event_staff_user_event
    ON event_staff(user_id, event_id);

CREATE INDEX IF NOT EXISTS idx_events_name_trgm
    ON events USING gin (lower(name) gin_trgm_ops);

