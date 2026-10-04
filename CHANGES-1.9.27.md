# v1.9.27 — Production checkout recovery + organizer operations

- Fixed payment checkout recovery for stale provider-order references: local provider IDs are cleared only after an explicit provider NOT_FOUND response; transient gateway failures remain fail-closed.
- Razorpay 404 responses now preserve a typed provider-not-found status so stale local references can be recovered safely.
- Added a top-level **Issued Tickets** organizer/platform console with server-side authorization scope, event filtering, search, status/source filters and pagination.
- Existing event-scoped **Issued Tickets**, **Orders**, attendee CSV export and event report summary remain available from Event Operations.
- CSV export from Event Operations now uses the authenticated API path instead of a raw browser URL, preserving the Authorization interceptor.
- Added **Share ticket** and **Save as PDF** actions to the generated-ticket page, payment-result page and recovery page. Mobile browsers use the native share sheet; desktop browsers fall back to copying the secure ticket link. Save as PDF opens the browser's PDF save flow with print-only controls hidden.
- Fixed ticket share-link construction so the `access=` credential is not double-prefixed.
- Formalized the existing Neelastack Platform Admin → Organizer → Organizer Team → Event Manager / Gate Staff access model without weakening existing event scoping.
- Hardened deployment dotenv handling: `infra/deploy/deploy.sh` no longer sources the complete `.env`; Docker Compose remains the authoritative dotenv parser, so custom values such as `MAIL_FROM="Neelastack Events <events@neelastack.com>"` cannot break Bash deployment.
- No existing backend test source was modified or removed; v1.9.27 adds only additive contract coverage.
