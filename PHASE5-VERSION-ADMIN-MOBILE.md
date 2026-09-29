# Phase 5 — Neelastack baseline alignment, admin mobile UX and production hardening (1.4.2)

## Version alignment

The project now declares the same overlapping framework/runtime versions as the supplied Neelastack application: Spring Boot 4.0.8 / Java 21 on the backend and Angular 20.3.30 + Angular SSR/build 20.3.36 / Node 22 baseline on the frontend. The exact reference values are documented in `docs/NEELASTACK-VERSION-BASELINE.md`.

## Admin

- Added a mobile event-card view instead of forcing the 900px desktop event grid on phones.
- Kept the desktop table for wide screens.
- Manager workspace visibly states `ASSIGNED EVENTS ONLY`.
- Rich event editor remains available only to platform admins/organizer owners.

## Security

- EVENT_MANAGER remains operational only: assigned-event access, scanning, and complimentary issuance.
- EVENT_MANAGER is not a financial refund approver. Refund authorization is restricted to platform admin or organizer OWNER/FINANCE roles.

## SSR/runtime

The Angular SSR runtime image now installs production dependencies after copying the built server, matching the proven Neelastack container pattern. This prevents a clean runtime image from failing when the SSR entrypoint externalizes Express.
