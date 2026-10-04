-- Allow ticket sales to remain open through the full multi-day event.
-- Keep bookingStart < eventStart and bookingEnd <= eventEnd enforced in the service layer;
-- the database constraint is limited to invariants that are safe for existing production rows.
ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_event_booking_window;
ALTER TABLE events ADD CONSTRAINT chk_event_booking_window
    CHECK (
        (booking_starts_at IS NULL OR booking_ends_at IS NULL OR booking_ends_at > booking_starts_at)
        AND (booking_ends_at IS NULL OR booking_ends_at <= COALESCE(ends_at, starts_at))
    );
