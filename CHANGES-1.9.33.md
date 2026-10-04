# v1.9.33 — Deployment qualification correction

## Deployment blockers fixed

- **Flyway V26 fresh-database failure:** `tickets.issued_at` did not exist. V26 now creates only the valid `orders(event_id, created_at DESC, id DESC)` cursor index; ticket cursor indexes remain in V28 against `tickets.created_at`.
- **Backend Maven test blocker:** `MultiEventContractTest` was asserting an implementation-specific repository call string that the current cursor/aggregate dashboard implementation intentionally does not use. The contract now checks the real `event_manager_assignments` authorization boundary.
- **Organizer issued-ticket contract blocker:** the organizer panel now explicitly displays the default **All events** scope while the server continues to enforce organization/event authorization.
- **Enterprise qualification contract blocker:** the hostname assertion now recognizes the production guard's escaped regular-expression form (`events\.neelastack\.com`).

## Regression hardening

- Cleaned a malformed selector in the issued-ticket admin component stylesheet (`}..alert` → `}.alert`) so the generated component CSS stays valid and deterministic.

- Added a migration contract that fails the test suite if `issued_at` is reintroduced into V26.
- Retained the valid ticket cursor ordering on `tickets.created_at` in V28.
- No payment, authorization, inventory, ticket issuance, QR or check-in security behavior was weakened.

## Runtime version

- Release version: **1.9.33**
- Flyway migrations remain **V1–V28**
