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


## Local E2E

Local browser qualification is supported for an isolated local stack. Start the application at `http://127.0.0.1:4002`, then run with `E2E_ENV=local E2E_BASE_URL=http://127.0.0.1:4002`. Production origins are always refused. Read-only public-security coverage can run without stateful test secrets; the full suite still requires disposable local admin/staff credentials, event/ticket IDs, and ticket/QR credentials.


### One-command Windows smoke test

From the repository root:

```powershell
.\infra\local-e2e.ps1
```

This starts PostgreSQL, Redis, backend, SSR web, and edge, waits for `http://127.0.0.1:4002/edge-health`, installs Chromium through the direct Playwright CLI script, and runs the public-security browser gate.
## Refresh local authentication tokens

When a PowerShell session has been restarted, refresh the disposable local admin/staff credentials in one step by dot-sourcing:

```powershell
cd C:\Users\bikku\OneDrive\Desktop\lakhdatar-events\e2e
. .\refresh-local-bearers.ps1
```

The helper prompts for both passwords, never writes them to disk, and verifies the resulting admin/staff bearer tokens against the local edge before returning. It does not create accounts, bypass MFA, weaken authentication, or target production.
