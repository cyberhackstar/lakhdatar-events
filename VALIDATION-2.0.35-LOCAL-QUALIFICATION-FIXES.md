# Validation — Neelastack Events v2.0.35 Local Qualification Fix 1

## Included production/runtime fixes

- Windows-safe frontend npm bootstrap/doctor from the v2.0.35 release candidate.
- Angular SSR and dependency hardening already present in the runtime hotfix 2 base.
- Transactional/recovery cleanup hardening already present in runtime hotfix 1.
- Structured logging `provider` + `provider.*` collision guard already present in runtime hotfix 2.
- Ticket access-token, QR credential and paid-ticket/order-count behavior preserved.
- Exact slashless `/api/v1/public/events` edge route added to the standard NGINX edge.
- The same exact slashless catalog route added to the HA NGINX example.
- iOS Safari scanner E2E selector corrected to avoid Playwright strict-mode failure when both camera DOM and fallback state DOM are mounted.
- Public browser qualification now directly tests `/api/v1/public/events?page=0&size=12` with redirects disabled, so a future NGINX 301 regression fails immediately.
- Backend release contract and platform baseline checks now require the exact slashless catalog route in both standard and HA edge configurations.

## Packaging-environment validation

PASS — `node tools/verify-frontend-lock.mjs`

PASS — `node tools/verify-platform-baseline.mjs`

PASS — JavaScript syntax: `e2e/tests/staff-scanner-ui.spec.js`, `e2e/tests/public-security.spec.js`, `tools/verify-platform-baseline.mjs`

PASS — NGINX syntax validation of the updated edge configuration using a temporary self-contained test wrapper.

## Windows runtime validation to perform next

From the repository root:

```powershell
docker compose up -d --build backend web edge
docker exec lakhdatar-dev-edge nginx -t
curl.exe -i "http://127.0.0.1:4002/api/v1/public/events?page=0&size=12"
```

Expected catalog API result:

```text
HTTP/1.1 200
```

There must be no 301/302 redirect to `/api/v1/public/events/`.

Then run:

```powershell
cd e2e
npm ci
npm test -- tests/public-security.spec.js --project=chromium
npm test -- tests/staff-scanner-ui.spec.js --project=ios-safari
```

For the full mutation suite, refresh the dedicated local admin/staff bearer tokens and use a fresh disposable E2E QR credential before each stateful run. Do not weaken authentication to make the test pass.

## Packaging limitation

A fresh Maven `clean verify` was not executed in this packaging environment because Maven was unavailable and package-manager installation did not complete within the packaging window. The repository's previously recorded v2.0.35 backend qualification remains preserved in the base release; the new backend contract assertions are source-checked and are intended to run in the user's Windows Maven build before staging promotion.
