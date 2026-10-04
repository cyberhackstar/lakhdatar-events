# v1.9.26 — Checkout recovery, operations console, ticket sharing

- Safely retries provider-order creation when a receipt lookup definitively returns not-found; uncertain provider outages still fail closed.
- Added organizer/event operations APIs for paginated issued tickets and orders.
- Added Event Operations console with issued-ticket search/filter/pagination, order search/filter/pagination, and CSV export.
- Added Share Ticket and Save as PDF actions to the customer ticket experience and payment-result page.
- Preserved platform-admin → organizer → team → event assignment security model.
- No existing test source removed or rewritten.

### Operations UX hardening
- Added a top-level organizer/platform Issued Tickets view with server-enforced scope, event filtering, search and pagination.
- CSV export stays on the authenticated API path so the browser Authorization interceptor is used.
- Share links preserve the original ticket access credential without double-encoding the `access=` prefix.
