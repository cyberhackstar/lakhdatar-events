# Validation — 1.9.38

- Neelastack stability baseline: PASS.
- Version metadata aligned to 1.9.38.
- Finance service contains no `l.entry_id` reference.
- Public sales state no longer closes bookings at event start for multi-day events.
- Existing start times on in-progress events may be retained during updates; newly changed starts must be future-dated.
- Cashfree checkout target is `_self`.
- Admin event CSV downloads use native same-origin browser navigation rather than Angular XHR/blob handling.
- Admin event NGINX path is explicitly buffered before the generic `/api/` path.
- Flyway V29 present for booking-window database validation.
- Frontend release should be validated by CI with `npm ci --no-audit --no-fund`, production build, and unit tests.
- Backend release should be validated by CI/production build with the existing Maven test suite.

## Known external edge dependency

If Chrome continues to report `ERR_QUIC_PROTOCOL_ERROR` on a direct CSV download after this release, the remaining issue is at the Cloudflare HTTP/3 edge path rather than the Angular XHR path. The recommended network-level fallback is to compare the same request over HTTP/1.1 and, only if confirmed, apply a narrowly scoped Cloudflare response-header transform to remove `Alt-Svc` for the affected hostname.
