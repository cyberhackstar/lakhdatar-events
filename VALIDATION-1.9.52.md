# Validation — v1.9.52

## Release scope
This release closes the v1.9.51 production blockers and hardens payment/refund, worker isolation, authentication, check-in privacy, and deployment safety.

## Static/source validation
- Release version is `1.9.52` in VERSION, backend POM, frontend package metadata, and active production configuration.
- No business payment/reconciliation/refund path uses arbitrary `findFirst()` provider-payment selection. Exact provider transaction IDs are preferred; ambiguous fallback matches fail closed.
- Razorpay refund webhook handling validates the signed event, resolves the trusted local payment, validates amount, and reconciles provider refunds by provider refund ID.
- Periodic reconciliation includes `COMPLETED` payments and inspects provider refund transactions when Razorpay reports a refunded/amount-refunded payment, covering missed refund-webhook delivery.
- Provider refunds are idempotently stored by provider refund ID and may be recorded multiple times per payment.
- Refund totals are cumulative and fail closed if provider refunds exceed the original payment amount.
- Receipt-based late-payment recovery queues compensation immediately after the capture transaction commits, with the refund recovery sweep as fallback.
- Event-A check-in failures for Event-B/invalid credentials do not expose the submitted ticket details in the API response.
- Forced-password-change users remain blocked from authenticated business APIs but do not get blocked from public/catalogue, webhook, setup, or health routes when a stale bearer token is present.
- API replicas with `WORKER_ENABLED=false` do not submit local mail workers, payment/reconciliation/recovery/expiry schedulers, or token-cleanup work.
- Existing deployments require a successful PostgreSQL backup before deployment; first install is the only `--allow-missing` path.
- Historical failed refund attempts are retained; explicit retries receive a fresh local refund ID/receipt/idempotency key.
- Release ZIP excludes `node_modules`, Angular build-cache artifacts, and Maven `target` outputs.

## Contract/static test execution in this environment
- `node tools/verify-platform-baseline.mjs` — **PASS**
- Pure Java/JUnit release contract tests (78 tests) — **PASS 78 / FAIL 0**
- Java source parse-only scan across 122 backend source files — **no Java parse errors detected**; unresolved third-party/dependency diagnostics are expected because Maven dependencies are unavailable here.
- `bash -n infra/deploy/deploy.sh` — **PASS**
- Production Compose YAML parse — **PASS**
- HA Compose YAML parse — **PASS**
- Root Compose YAML parse — **PASS**
- Flyway migration continuity — **PASS V1..V32 contiguous**
- Secret/build-artifact scan — **PASS** (no `.env`, private-key, keystore, or known provider credential file shipped)

## Build status
- Full Maven `clean verify` was not executable in this environment because Maven is not installed and the package has no Maven wrapper; the project must execute this in CI/your deployment environment.
- Full Angular/Karma build was not executable here because the dependency cache is unavailable and network/DNS access prevented a complete `npm ci`. The release source and lockfile were kept intact, and local `node_modules` was removed before packaging.

## Production qualification
The source package is hardened for production deployment, but the release is considered build-qualified only after the normal CI pipeline completes the Java/Maven and Angular production builds plus integration tests against its provisioned PostgreSQL/Redis/Testcontainers environment.
