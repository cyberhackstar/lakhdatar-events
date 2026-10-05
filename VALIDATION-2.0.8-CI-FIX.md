# v2.0.8 CI Regression Fix Validation

Date: 2026-10-05

## Root causes reproduced from CI logs

1. Dependabot PR #19 changed the frontend dependency spec from Express 4 to Express 5 (`^5.2.1`) and the matching `@types/express` major line. The existing stability verifier treated the old Express 4 spec as the only permitted value, so the frontend job failed before dependency installation.
2. The Docker edge regression returned an absolute `Location` containing the internal `:8080` origin port even though the configuration requested a relative `/monitor` redirect. NGINX was applying its default `absolute_redirect` behavior.

## Fixes

- The baseline verifier now allows only the two explicitly controlled Express combinations:
  - `express: ^4.22.2` + `@types/express: ^4.17.21`
  - `express: ^5.2.1` + `@types/express: ^5.0.6`
- `absolute_redirect off;` is enforced in the primary edge and HA NGINX server blocks.
- The existing CI regression still requires the monitor root `Location` to be exactly `/monitor`; no assertion was weakened.

## Local validation

- `node --check tools/verify-platform-baseline.mjs` — PASS
- `node --check tools/verify-frontend-lock.mjs` — PASS
- `node tools/verify-platform-baseline.mjs` — PASS
- `node tools/verify-frontend-lock.mjs` — PASS
- Baseline verifier simulation with Dependabot Express 5 specs — PASS
- Primary edge NGINX syntax test — PASS
- Primary edge monitor redirect integration test: `Location: /monitor` — PASS
- Primary edge `/edge-health` smoke test — PASS
- HA NGINX syntax test — PASS

Full GitHub Actions build, tests, security scans, image attestation and signing remain CI-authoritative.
