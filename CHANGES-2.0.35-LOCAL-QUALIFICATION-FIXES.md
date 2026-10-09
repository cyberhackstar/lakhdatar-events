# Neelastack Events v2.0.35 — Local Qualification Fixes

## Permanent fixes in this candidate

- Added an exact NGINX route for `/api/v1/public/events` so the edge no longer auto-redirects the canonical slashless API to `/api/v1/public/events/`, which Spring Boot correctly rejects with 404.
- Applied the same exact catalog route to the HA NGINX example so the production topology keeps identical API routing semantics.
- Hardened the iOS/Android/desktop staff-scanner E2E selector against Playwright strict-mode failures when the camera element and camera-unavailable state card coexist in the DOM.
- Added direct public-catalog API qualification with redirects disabled so future E2E runs fail immediately on an NGINX canonicalization regression instead of masking it behind a final 404.
- Added backend/CI contract checks for the exact slashless catalog route in both standard and HA edge configurations.

## Preserved fixes already present in v2.0.35 runtime hotfix 2

- Transactional/cleanup hardening from runtime hotfix 1.
- Structured logging collision protection for `provider` plus nested `provider.*` ECS fields.
- Ticket access-token and QR credential protections.
- Ticket/order count display and server-authoritative scanner response fields.
- Angular dependency and Windows npm bootstrap hardening.

## Deliberately not changed

- No database migrations were added.
- No payment-flow behavior was changed.
- No authentication policy was weakened to make E2E login pass.
- No credentials or environment secrets are embedded in the release.
