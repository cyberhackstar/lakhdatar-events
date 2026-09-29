# Lakhdatar Events — Phase 2A Validation

## Scope
Premium frontend UX, checkout/recovery experience, digital-ticket access flow, mobile scanner lifecycle, role-aware navigation, and event-day usability.

## Automated/static checks completed
- Repository source tree reviewed against the Phase 1 release.
- Inline Angular template sanity checks: PASS.
- Template/form module consistency checks: PASS.
- TypeScript parser/syntax pass: no TypeScript syntax diagnostics were found. External Angular/RxJS/html5-qrcode module-resolution diagnostics are expected because frontend dependencies are not installed in this environment.
- Shell syntax (`bash -n`) for all infrastructure scripts: PASS.
- JSON validation for Angular/package/tsconfig files: PASS.
- YAML validation for Compose and GitHub Actions files: PASS.
- Source hygiene sweep: no debug logging, `debugger`, TODO/FIXME markers, external Google font imports, frontend Razorpay secret references, or ticket `?token=` URL usage found.
- Sensitive local files (`.env`, key/certificate/credential files) are not included in the project tree.

## Phase 2A functional/code changes
- Public event pages now react to ticket sale windows and minimum purchase quantities.
- Ticket quantity controls respect `minPerOrder` and `maxPerOrder`.
- Checkout holds and countdown state are explicit and safe.
- Checkout idempotency is stable per event/cart/customer identity for the short reservation lifetime.
- Payment-result recovery survives navigation/refresh through session storage.
- Ticket access credentials are passed in `X-Ticket-Token` rather than normal URL query strings; legacy query-token support remains server-side for compatibility.
- Mobile scanner pauses camera capture while showing results, when the page becomes hidden, when offline, and while manual entry is open.
- Manual validation uses the same authoritative server endpoint as QR scans.
- Staff console has loading, empty, retry and error states.
- Admin console has explicit loading/error/retry states and uses the real Lakhdatar logo.
- Login return URLs are restricted to local application paths to prevent open redirects.
- Token refresh uses a single-flight request to avoid concurrent refresh storms.
- Logout revokes the server-side refresh token.
- SUPPORT accounts can access the operations dashboard read-only when explicitly mapped to an organizer as SUPPORT; privileged event-management/refund actions remain blocked server-side.
- CI package publishing permission is limited to the Docker publishing job.
- External font loading was removed so the premium UI remains compatible with the production CSP.

## Build limitation
This environment does not have Docker or Maven installed and cannot resolve the npm/Maven package registries, so a dependency-resolved Spring Boot build, Angular production build, Docker ARM64 build, and live Razorpay transaction could not be executed here. Those checks remain enforced in GitHub Actions CI/CD.

## Next phase
Phase 3 should perform actual dependency-resolved builds, high-concurrency payment/inventory/check-in testing, security abuse testing, ARM64 container validation, Oracle VM deployment rehearsal, backup/restore drill, and rollback drill.
