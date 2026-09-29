# Phase 3 Verification Boundary

This phase adds payment recovery, concurrency, authentication and rate-limit hardening plus k6 load-test scenarios.

The source tree has been statically checked for JSON/YAML validity, TypeScript syntax, Java parse-level errors, shell syntax, duplicate imports, obvious debug markers, version drift and secret patterns.

The environment used to prepare this release does not have Maven/Docker installed and cannot resolve external package registries. Therefore the authoritative dependency-resolved build remains GitHub Actions, followed by the Oracle ARM64 deployment rehearsal.

Before live event use, execute the following in CI/staging:

1. `mvn -B -ntp clean verify` in `backend`.
2. `npm install --no-audit --no-fund && npm run build` in `frontend`.
3. Build/push both Docker images for `linux/arm64`.
4. Run the PostgreSQL/Testcontainers concurrency suites.
5. Run k6 against a staging backend using `infra/loadtest/checkin.js` and `infra/loadtest/public-event.js`.
6. Test Razorpay webhooks and late-payment recovery with test payments.
7. Execute backup/restore and application rollback drills before production cutover.
## Provider contract verification

The Phase 3 refund path uses Razorpay's current documented `X-Refund-Idempotency` header and the refund object's `receipt` field for safe retry/recovery. Razorpay's current API specification also documents refund statuses (`pending`, `processed`, `failed`) and the payment/order amount fields used by reconciliation.
