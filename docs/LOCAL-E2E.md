# Local E2E Qualification

This project supports a local Playwright mode in addition to the protected staging mode.

## Start the local stack

From the repository root (Docker Desktop required):

```powershell
Copy-Item .env.example .env
# Edit .env with local-only secrets
docker compose up --build -d
docker compose ps
```

The local edge is published on `http://127.0.0.1:4002`.

## Install browser test dependencies

```powershell
cd e2e
npm ci
npx playwright install chromium
```

For the full cross-browser suite on a development machine, install the other browsers too:

```powershell
npx playwright install chromium firefox webkit
```

## Public read-only smoke

```powershell
$env:E2E_ENV='local'
$env:E2E_BASE_URL='http://127.0.0.1:4002'
npx playwright test tests/public-security.spec.js --project=chromium
```

## Full local E2E

The stateful tests need disposable local data and credentials. Set these before running:

```text
E2E_ADMIN_BEARER
E2E_STAFF_BEARER
E2E_STAFF_EMAIL
E2E_STAFF_PASSWORD
E2E_EVENT_ID
E2E_TICKET_TYPE_ID
E2E_CHECKOUT_EMAIL
E2E_IDEMPOTENCY_KEY
E2E_TICKET_ID
E2E_TICKET_TOKEN
E2E_QR_TOKEN
E2E_GATE
```

Then:

```powershell
$env:E2E_ENV='local'
$env:E2E_BASE_URL='http://127.0.0.1:4002'
$env:E2E_RUN_MUTATIONS='true'
npx playwright test --reporter=line
```

Never point the mutation suite at production.
