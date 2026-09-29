# Lakhdatar Events — Phase 2A Notes

## Scope
Frontend correctness, premium public/checkout/ticket experiences, mobile scanner UX, authentication refresh lifecycle, safe ticket-link handling, and role-aware navigation.

## Key changes
- Premium responsive public event, checkout, ticket, login, staff, scanner, admin and recovery experiences.
- Checkout reuses a short-lived session idempotency key so browser/network retries do not create a new order accidentally.
- Payment result is retained in session storage for recovery after navigation/refresh.
- Ticket access credentials are sent via `X-Ticket-Token`; legacy query-token support remains only for compatibility.
- Scanner stops the camera while showing a result, on page hide, and on offline status; it never grants entry without server validation.
- Added manual ticket credential entry as a camera fallback using the same backend validation API.
- Added sale-window awareness to public ticket cards.
- Added safe post-login return URL handling.
- Added single-flight token refresh and server-side refresh-token revocation on logout.
- Added polished loading/error/empty states on the staff gate-selection screen.
- Added SUPPORT role to the operations guard and prevented unsupported CUSTOMER accounts from being sent into admin.
- Removed external font loading because the production CSP intentionally blocks third-party style imports.

## Validation performed
- Repository-wide source/static checks.
- TypeScript syntax pass with unresolved external modules filtered separately because npm dependencies are not available in this environment.
- YAML/JSON/shell syntax checks.
- No obvious debug statements, TODO/FIXME markers, external font imports, frontend Razorpay secrets, or ticket tokens in URLs in the current source.
- Docker/Angular/Maven end-to-end builds remain CI-gated because this environment does not have Docker and cannot resolve the npm/Maven registries.

## Phase 2 boundary
Phase 2A intentionally does not claim that Oracle ARM64 deployment, live Razorpay payments, or high-load event-day concurrency has been executed here. Those are Phase 3/4 validation activities.

## Additional hardening in final Phase 2A pass
- Ticket quantity controls now respect `minPerOrder` so the UI cannot create an invalid below-minimum cart by accident.
- Scanner camera is paused while the manual credential dialog is open, avoiding accidental background QR acceptance while an operator types.
- Staff and admin consoles now have explicit loading, empty and retry/error states.
- Support accounts are treated as read-only operations users when explicitly associated with an organizer as SUPPORT; event creation/publishing/refunds remain blocked server-side.
- CI package-publish permissions are scoped to the Docker publishing job instead of the entire workflow.
