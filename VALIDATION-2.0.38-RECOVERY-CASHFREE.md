# Validation Record — v2.0.38 Recovery + Cashfree Follow-up

## Changes included

- Missing provider-order recovery only selects `CREATED` and `AWAITING_PAYMENT` orders; expired/cancelled/confirmed orders are excluded by the repository query.
- Fresh `NOT_CREATED` payments go directly to provider-order creation, avoiding a predictably missing receipt lookup before the first Cashfree create call.
- Receipt lookup remains in place for ambiguous prior attempts (`CREATING` / `RECOVERY_PENDING`) and after a create call fails, so an order created remotely but not persisted locally can still be recovered.
- An expected 404 during explicit Cashfree receipt recovery is logged as a debug-level lookup miss. A 404 for a known provider resource remains a warning and is handled by the existing recovery logic.
- Added an offline Cashfree HTTP-contract test for the v2025-01-01 headers, idempotency key, expected missing receipt, order creation response, and successful payment response parsing.
- Retained the earlier v2.0.38 stale/expired-payment recovery filter and 10-minute staging E2E fixture timing.

## Checks completed in this workspace

- Node syntax checks passed for `e2e/support/provision.js` and `e2e/selftest/run-selftest.js`.
- Source-level contract checks passed for the recovery query/status filter, prior-state gating, Cashfree expected-404 handling, test presence, and fixture timing.
- Whitespace checks passed for the modified Java and Markdown files.
- ZIP integrity checks are run at packaging time.

## Checks not completed here

- `mvn -B -ntp clean verify` was not run: Maven is unavailable in this workspace, and the project dependencies are not cached locally.
- `node e2e/selftest/run-selftest.js` could not start because the `qrcode` dependency is not installed; dependency installation previously timed out.
- The Java JUnit HTTP-contract test is added but not executed here because Maven/dependencies are unavailable.
- No real Cashfree sandbox transaction, webhook delivery, or production settlement has been executed from this workspace. Before production release, perform a sandbox payment and verify both the signed webhook path and the server-side order-payments reconciliation path.

## Interpretation of the original staging log

The observed `GET /pg/orders/{order_id}` 404 happened during receipt lookup before the first order create request in the same checkout trace. Cashfree's published integration examples confirm the `/pg/orders` endpoint and `x-api-version: 2025-01-01`; the log alone does not establish a failed order create or failed payment settlement. This release removes the unnecessary fresh-order lookup and keeps lookup for ambiguous retry recovery.
