# v1.9.28 Validation Report

## Scope
Final production-package validation for the Lakhdatar Events Platform v1.9.28 release.

## Passed in packaging sandbox
- Platform baseline validator: PASS (`node tools/verify-platform-baseline.mjs`).
- Version consistency: PASS — VERSION, Maven, package.json and package-lock at 1.9.28.
- JSON/package-lock parsing: PASS.
- YAML parsing: PASS for `docker-compose.yml` and `infra/docker-compose.prod.yml`.
- Shell syntax: PASS for all repository `.sh` files.
- TypeScript syntax/transpile check: PASS for 61 frontend `.ts` files.
- Java parser check: PASS for 160 Java files; the local `javac` invocation exits non-zero only because project dependencies are not installed, with no parser-level syntax diagnostics.
- Flyway sequence: PASS, contiguous V1–V24.
- Checkout recovery contract: PASS; the deprecated permanent `PAYMENT_PROVIDER_UNCERTAIN` production block is absent from production source.
- Provider NOT_FOUND recovery contract: PASS.
- Organizer ticket scope contract: PASS.
- Search query bound: PASS (100-character service-side limit).
- Ticket Share/PDF contract: PASS, including actual `ActivatedRoute.snapshot.fragment` parsing, native share fallback, and PDF route generation.
- Deployment dotenv contract: PASS; deploy script does not source the complete `.env`, Compose receives the dotenv file directly, and `.env.example` quotes custom `MAIL_FROM` safely.
- Existing v1.9.25 backend test sources: PASS — 41 common files, 0 changed, 0 missing.
- Total backend test files in v1.9.28: 47 (6 additive contract tests).
- No `.env`, private key, build JAR, ZIP or log artifacts included in the release workspace.

## Customer / organizer feature coverage
- Customer payment-result page: Share each issued ticket + Save PDF.
- Ticket page: Share + Save PDF + recovery.
- Recovery page: Share + Save PDF for every recovered ticket.
- Organizer/event-manager console: issued-ticket search, filters and pagination.
- Event Operations: issued tickets, orders, attendee CSV and metrics.
- Platform Admin: organizer management and cross-organizer oversight.
- Organizer owner/team: event managers and gate staff with event-scoped assignments.

## Mandatory external release gates
These were not executable in this packaging sandbox and must pass in CI/VM before production promotion:
- `cd backend && mvn -B -ntp clean verify`.
- `cd frontend && npm ci --no-audit --no-fund && npm run build && npm test`.
- Multi-architecture Docker build/push and container startup health checks.
- PostgreSQL/Redis runtime checks and Flyway startup against the production volume.
- Oracle VM deployment smoke test through Cloudflare (`https://events.neelastack.com`).
- Razorpay/Cashfree live or sandbox payment create → return → server verification → ticket issuance flows.
- Email delivery using the configured Gmail SMTP account/custom From address.

## Important production security action
The Gmail app password previously pasted into the conversation/logs must be revoked and replaced before production use. Store the replacement only in the VM secret environment/secret manager.
