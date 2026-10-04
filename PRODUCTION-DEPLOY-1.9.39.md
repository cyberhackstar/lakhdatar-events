# Neelastack Events v1.9.39 — Production deployment notes

## Release scope

This release is the consolidated reliability correction after the v1.9.36 production observations. It keeps payment verification authoritative on the backend and does not weaken lifecycle or inventory invariants.

## Included production corrections

- Cashfree uses hosted same-tab checkout (`redirectTarget: '_self'`) and the checkout-session cookie is scoped to `/` so the payment return page can perform server-side verification.
- Finance ledger cursor and page APIs read provider `public_id` UUIDs instead of internal BIGINT payment/refund keys.
- Attendee CSV export is bounded in memory and returned with an explicit `Content-Length`, `Content-Disposition`, `no-store`, and `nosniff` headers. Admin UI uses a native same-origin browser download instead of an XHR/blob pipeline.
- Event-level booking windows are allowed to remain open through the full event end. When booking end is not explicitly configured, the effective booking end is the event end. V29 enforces the invariant and V30 repairs historical multi-day rows that were capped at the start time.
- Ticket pages, PDF tickets, email content, and gate-scanner results show the total tickets/seats in the same order and the current ticket's position, e.g. `2 seats booked · ticket 1 of 2`.
- Scanner verification keeps the camera session alive between scans and presents an immediate verifying state; the server remains authoritative.
- Angular unit-test setup loads Zone.js/zone.js/testing; typed API query objects remain compatible with the central query-parameter sanitizer.

## Deployment sequence

1. Run GitHub Actions CI on the release commit. Required frontend gates are baseline verification, `npm ci`, production SSR build, unit tests, production bundle validation, and SSR smoke test. Required backend gate is `mvn -B -ntp clean verify` with Testcontainers.
2. Build and publish the three multi-architecture images: backend, web, and edge (`linux/amd64,linux/arm64`).
3. Deploy the exact immutable commit SHA to the Oracle VM using `infra/deploy/deploy.sh`.
4. Allow Flyway to migrate the live database from V28 to V30. Do not manually edit `flyway_schema_history` and do not skip V29/V30.
5. Run the existing public smoke tests through Cloudflare.
6. Verify one real event configured with a start date before its multi-day end date: booking remains possible after the first day and until the configured booking end/event end.
7. Verify a two-ticket order: each ticket page shows `2 seats booked in this order`, and the gate scan shows `2 seats booked · ticket N of 2`.
8. Verify Cashfree in the production environment with a small authorized test transaction and confirm return-page server verification before treating payment as complete.

## Cloudflare HTTP/3 note

The historical admin CSV symptom was `ERR_QUIC_PROTOCOL_ERROR 200 (OK)`. The application response has been changed from streamed chunked delivery to a bounded byte response, and the browser now uses a native download. If the same QUIC error persists after this release is actually deployed, inspect the Cloudflare HTTP/3/edge path; disabling HTTP/3 for the hostname is an infrastructure fallback, not an application-layer payment or database fix.

## Lifecycle 409 note

A `409 Conflict` from `publish`, `complete`, or protected event update is still valid when an action violates the event state machine (for example, completing before the multi-day event has ended). The release keeps those safeguards. The frontend now blocks known-invalid actions where the state and timestamp are available and surfaces the server message instead of silently failing.
