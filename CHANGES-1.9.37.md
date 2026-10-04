# v1.9.37 — Payment, CSV transport, event lifecycle and multi-day booking hardening

## Fixed
- Cashfree checkout uses modal on desktop and full-page `_self` on mobile, avoiding the broken mobile embedded checkout path. Existing server-side verification remains authoritative.
- Edge CSP explicitly allows Cashfree API framing/form actions required by hosted checkout.
- Finance cursor ledger queries use the real `financial_ledger_entries.public_id` column.
- Attendee CSV export uses a native same-origin browser download; the streamed backend export remains memory-safe, while the Angular XHR blob path is removed from the browser critical path.
- Event lifecycle completion is now based on the event end (or start when no end exists), not the event start.
- Lifecycle APIs are retry-safe for duplicate same-state requests.
- Event editor prevents premature Complete actions, double lifecycle submissions, accidental payment-provider changes, and edits after finalization.
- Booking windows now apply through the event end for multi-day events unless an earlier booking end is explicitly configured.
- Event creation/editor expose booking start/end controls; booking end defaults to the event end for multi-day events.
- Ticket sale-end validation cannot exceed the event end.
- Added Flyway V29 to replace the historical single-day booking constraint without editing prior migrations.
