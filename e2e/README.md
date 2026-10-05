# Enterprise browser qualification

This is a disposable-staging E2E suite. Read-only public/ticket checks run across desktop Chromium, Android Chrome and iOS Safari; stateful checkout/check-in mutations intentionally run once on the desktop project to avoid consuming the same staging credentials multiple times. Run it only against an isolated staging environment. Do not point the mutation tests at `events.neelastack.com`.

Required variables for the complete gate:

- `E2E_BASE_URL` — HTTPS staging origin.
- `E2E_ADMIN_BEARER` — dedicated staging admin token.
- `E2E_STAFF_BEARER` — dedicated staging scanner/staff token.
- `E2E_EVENT_ID` — disposable staging event.
- `E2E_TICKET_TYPE_ID` — ticket type under that event.
- `E2E_CHECKOUT_EMAIL` — sink/test mailbox.
- `E2E_IDEMPOTENCY_KEY` — unique per run; CI should generate this outside the committed repository.
- `E2E_TICKET_ID` + `E2E_TICKET_TOKEN` — dedicated issued ticket for read/PDF validation.
- `E2E_QR_TOKEN` + `E2E_GATE` — dedicated ticket/gate pair for the two-scan idempotency test.

The checkout and scanner tests are intentionally stateful. Use disposable staging records, never production records.
