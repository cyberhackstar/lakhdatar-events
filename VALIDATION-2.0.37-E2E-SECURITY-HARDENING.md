# v2.0.37 — validation record

## Checks completed in this workspace

- JavaScript syntax for `e2e/support/provision.js`, `e2e/global-setup.js`, `e2e/selftest/mock-api.js`, and `e2e/selftest/run-selftest.js` — PASS (Node.js 22.16.0).
- In-process provisioning self-tests using temporary QR dependency shims — PASS (9 scenarios covering generated MFA credentials, least-privilege configuration, payment-mode preflight contracts, partial creation, ticket position/count, and cleanup recovery). The temporary shims do **not** validate actual QR image generation/decoding.
- `node tools/verify-platform-baseline.mjs` — PASS.
- `node tools/verify-frontend-lock.mjs` — PASS.
- YAML syntax parsing for 11 workflow/compose files — PASS.
- Version consistency across `VERSION`, backend POM, frontend/E2E package metadata, and lockfiles — PASS.
- Search for the two previous hard-coded MFA seed literals and fixed mock-admin password — PASS; no remaining matches in E2E source.

## Checks not completed here

- Dependency-backed E2E self-test and Playwright browser suite: `npm ci` could not complete because this workspace could not resolve the npm registry (`EAI_AGAIN`).
- Backend `mvn -B -ntp clean verify`: Maven is not installed in this workspace.
- Gitleaks: not installed locally. The GitHub `security` job must pass on the candidate commit to close the original CI failure.
- Frontend production build and unit tests: not run in this workspace. The available Node runtime is 22.16.0; repository tooling requires Node.js >=22.19 or 24.x. Use the repo-pinned runtime in CI.
- Real staging API compatibility, GitHub secret availability, Cashfree/Razorpay sandbox behavior, MFA login, payment flow, browser test results, and verified server cleanup remain unverified until the `enterprise-e2e-staging` workflow completes successfully.

## Mandatory release gates

A passing mock self-test is not production certification. Before promoting this candidate, require all exact-SHA CI checks, Gitleaks, Maven and frontend verification, a successful manual staging E2E run with `run_mutations=true`, database fresh-and-upgrade qualification, separate load tests, DAST/security evidence, backup/restore verification, signed image provenance, and protected release evidence. Do not promote if any gate is skipped, inconclusive, or fails.
