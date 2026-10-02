-- First clean-production release hardening. Keep prior migrations immutable and make only additive/fix-forward changes here.

-- Complimentary manager orders intentionally have a zero monetary total.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_total_positive;
ALTER TABLE orders ADD CONSTRAINT chk_order_total_non_negative CHECK (total_minor_units >= 0);

-- Replace the earlier booking-window constraint with the complete invariant set.
ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_event_booking_window;
ALTER TABLE events ADD CONSTRAINT chk_event_booking_window
    CHECK (
        (booking_starts_at IS NULL OR booking_ends_at IS NULL OR booking_ends_at > booking_starts_at)
        AND (booking_ends_at IS NULL OR booking_ends_at <= starts_at)
    );
ALTER TABLE ticket_types ADD CONSTRAINT chk_ticket_sale_window_valid
    CHECK (sale_ends_at IS NULL OR sale_starts_at IS NULL OR sale_ends_at > sale_starts_at);

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Safe response-time indexes for the public event catalogue's case-insensitive filters.
CREATE INDEX IF NOT EXISTS idx_events_name_trgm_published
    ON events USING GIN (lower(name) gin_trgm_ops) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_events_short_description_trgm_published
    ON events USING GIN (lower(coalesce(short_description, '')) gin_trgm_ops) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_venues_name_trgm ON venues USING GIN (lower(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_venues_city_trgm ON venues USING GIN (lower(coalesce(city, '')) gin_trgm_ops);
