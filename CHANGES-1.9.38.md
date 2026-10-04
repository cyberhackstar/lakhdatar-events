# Changes — 1.9.38

## Production reliability correction

- Fixed finance ledger cursor/page SQL to read the existing `financial_ledger_entries.public_id` column instead of nonexistent `entry_id`.
- Fixed customer-facing sales state so a published multi-day event remains available from event start through event end, subject to the explicit booking window and inventory.
- Fixed `bookingWindowOpen` to enforce published status and the full event end fallback when no booking end is configured.
- Fixed event creation and editing so an omitted booking end follows the event end; edits keep that implicit relationship when the event end changes.
- Prevented ticket sale windows from extending beyond the event end.
- Switched Cashfree web checkout to the documented hosted `_self` redirect to avoid the embedded checkout iframe CSP failure and the mobile blocking/blur state.
- Kept server-side Cashfree payment verification on the return URL.
- Switched both admin event CSV entry points to native same-origin browser downloads and buffered admin-event responses at the NGINX edge.
- Kept lifecycle operations retry-safe and hid `Complete event` until the event end has actually passed.
- Allowed safe metadata/booking-window edits on an in-progress multi-day event when its existing start time is unchanged; changing the start still requires a future instant.
- Expanded regression contracts for finance SQL, multi-day booking, Cashfree redirect, and CSV download behaviour.

## Database

Flyway `V29__multi_day_booking_window.sql` remains the only new schema migration for the booking-window correction and is compatible with the existing production schema.
