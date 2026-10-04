# Neelastack Events v1.9.39

## Reliability / production fixes
- Fixed finance ledger cursor/page mapping to use provider `public_id` UUIDs instead of internal BIGINT foreign keys.
- Reworked attendee CSV export to return a bounded byte response with Content-Length/no-store headers, avoiding fragile HTTP/3 streaming behavior.
- Expanded the Cashfree checkout-session cookie to `/` so the payment verification page can complete server-side verification after provider redirect.
- Retained Cashfree hosted redirect mode and production CSP allow-list for `api.cashfree.com`.
- Added Flyway V30 to repair historical multi-day events whose booking end was incorrectly capped at event start.
- Preserved the explicit booking-end override while defaulting multi-day event sales through the full event end.
- Added total tickets/seats in an order plus ticket position to ticket pages, PDFs, email, and gate-scan results.
- Kept ticket verification authoritative on the backend; count metadata is informational and does not grant entry.

## Event lifecycle
- Existing 409 lifecycle protections remain intentional for illegal transitions.
- Publishing is retry-safe for an already-published event; invalid transitions return clear conflict messages rather than weakening state invariants.
