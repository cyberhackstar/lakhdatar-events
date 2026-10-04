# Neelastack Events v1.9.40

## Production reliability correction

- Corrected the Angular event-editor strict TypeScript narrowing error around the effective event end.
- Made backend enterprise-scale contract tests resolve frontend fixtures from both repository-root and `backend/` Maven working directories.
- Configured Angular unit-test Zone.js and Zone.js testing as global test polyfills, removing dependence on individual spec import order.
- Preserved Cashfree hosted `_self` checkout, server-side payment verification, scanner camera reuse, native CSV download, finance public-id ledger queries, and multi-day booking-window behavior from the v1.9.39 baseline.
- Multi-day bookings remain valid through the effective event end when no explicit earlier booking end is configured.
- Event editing continues to carry an implicit booking end forward when it was previously tied to the old event end; explicit custom booking windows remain explicit.
- Ticket quantity metadata remains available on customer tickets/PDF/email and gate-scanner results as `ticket X of Y` and `Y seats booked`.
- Lifecycle protections remain server-authoritative, while the event editor now blocks known-invalid publish/unpublish/cancel/complete/archive actions before sending avoidable 409 requests.
