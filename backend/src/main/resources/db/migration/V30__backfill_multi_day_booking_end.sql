-- Repair legacy multi-day events created before the booking-window fix.
-- When a historical row ended sales at the event start despite having a later event end,
-- move the booking end to the real event end. Explicit earlier booking closes are preserved.
UPDATE events
SET booking_ends_at = ends_at
WHERE ends_at IS NOT NULL
  AND ends_at > starts_at
  AND (booking_ends_at IS NULL OR booking_ends_at <= starts_at);
