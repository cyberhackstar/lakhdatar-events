# Validation plan — v2.0.53 Cashfree order-expiry compatibility

## Scope

This candidate addresses the field-specific Cashfree error `order_expiry_time_invalid` recorded in staging on v2.0.52. It changes both the reservation lifetime default and the wire timestamp precision.

## Required local checks

1. `cd backend; mvn -B -ntp clean verify` — confirm all tests pass, including `CashfreeGatewayProviderHttpTest.createOrderSendsReservationExpiryWhenProvided`.
2. `cd frontend; npm ci; npm run verify:baseline; npm run verify:dependencies; npm test; npm run build`.
3. `cd e2e; npm ci; npm run selftest`.
4. Run repository CI contract checks and full secret scanning on the exact candidate commit.

## Required staging checks

1. Confirm deployed staging SHA equals the release SHA. In the backend container, inspect only `RESERVATION_HOLD` and confirm it is `20m`; do not print secrets.
2. Verify both Cashfree keys are a matched sandbox pair without displaying either value.
3. Run the mutation-enabled Playwright qualification on `https://staging-events.neelastack.com` using disposable fixtures.
4. Confirm Cashfree Create Order returns success; same-key retry resolves to the same logical order; payment confirmation reconciles; ticket issuance succeeds; scanner accepts the generated ticket exactly once.
5. Confirm teardown cancels the disposable event and deactivates test identities. Retain Playwright HTML and trace artifacts.

## Current verification status

The staging provider error has been diagnosed, but v2.0.53 is not yet built, deployed, or tested in staging by this plan. This document is a checklist, not a claim that these gates have passed. Production remains prohibited until all required checks and protected readiness evidence pass for the same SHA.
