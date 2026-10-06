# v2.0.15 — Enterprise Financial Recovery + Security + Resilience

This release continues the enterprise hardening program with financial recovery correctness, event/check-in concurrency protection, durable payment webhook processing, privileged MFA, password recovery, backup/DR controls, deployment safety and release qualification gates.

## Enterprise production status

v2.0.15 is the **enterprise-production certification-gated release**. Production HA promotion is fail-closed on the protected certified Git SHA, signed image provenance, HA topology, privileged MFA, external database/Redis, and protected certification evidence.

The repository must never self-declare a runtime/HA/DR/security certification result that has not actually been executed and reviewed in the target environment. See `docs/ENTERPRISE-PRODUCTION-CERTIFICATION.md`.

---

## v1.9.48 — CI and Neelastack branding correction

Enterprise multi-event ticketing platform with server-authoritative payments, admission verification, multi-day booking windows, finance/reconciliation, event lifecycle controls, and mobile gate scanning.

CI/runtime correction: whitespace-stable production contract assertions, supplied Neelastack branding asset, 48px shared mark rendering, and preservation of the payment/scanner/CSV/finance/booking protections.

See `CHANGES-1.9.48.md`, `VALIDATION-1.9.48.md`, `PRODUCTION-DEPLOY-1.9.48.md`, and `RELEASE-MANIFEST.txt`.

Enterprise application/worker separation is supported through `WORKER_ENABLED`; HA reference deployment, PITR runbook, operations health and k6 load scenarios are under `infra/ha`, `infra/backup`, `infra/monitoring` and `infra/loadtest`. See `docs/ENTERPRISE-RELEASE-QUALIFICATION.md` for the production qualification gates.

# Release 1.9.44 — frontend test-runtime correction

See `CHANGES-1.9.44.md`, `VALIDATION-1.9.44.md`, `IMPLEMENTATION-STATUS.md`, `RELEASE-NOTES.md`, `ADMIN-SETUP-GUIDE.md` and `ENV-LINKAGE-AUDIT.md`.

# Release 1.9.21 — production smoke-test + SSR proxy correction

This release retains the v1.9.13 Alpine/OpenSSL security hardening and corrects the final container supply-chain verification step by using GitHub Artifact Attestations for the exact pushed image digests, followed by GitHub CLI verification and Cosign signing.

This release rebuilds from the verified v1.9.11 source baseline and fixes the CI regressions observed when the release was pushed: Testcontainers dependency management/version drift, release-version drift, shallow Gitleaks history, and unsupported dependency-review execution. It also retains the registry-backed security patches and multi-architecture image publication.

This release corrects a production Redis container hardening conflict found during Oracle ARM64 deployment and removes an incorrect separate Cashfree webhook-secret requirement: Cashfree webhook signatures are verified with the provider Secret Key. Redis now starts directly as its non-root UID/GID while retaining `no-new-privileges` and `cap_drop: ALL`; the production healthcheck authenticates through `REDISCLI_AUTH`. Existing PostgreSQL and Redis named volumes are preserved.

# Neelastack Events Platform

Premium multi-event ticketing and event-day admission platform, **owned and operated by Neelastack**.
The first organizer on the platform is **Lakhdatar Events**; more organizers can be added without code changes.

| Role | Entity |
|---|---|
| Platform / software owner | **Neelastack** |
| Current organizer (event company) | **Lakhdatar Events** |
| Public site | https://events.neelastack.com |

> Neelastack is the technology company. Lakhdatar Events is a customer whose events are sold here. Organizer data lives in the
> `organizers` table and is rendered dynamically; nothing in the core architecture hard-codes an organizer.

## Enterprise production scale

- Financial operations console with append-only ledger entries for sales/refunds and organizer-scoped reconciliation.
- Cursor pagination for large issued-ticket and order datasets, plus indexed query paths.
- Server-generated ticket PDFs protected by the same short-lived ticket access credential.
- Distributed locks keep state-changing scheduled recovery jobs safe when multiple backend replicas run.
- Reference multi-VM HA topology with external PostgreSQL/Redis dependencies. A single VM remains a single failure domain and is not called HA.
- PostgreSQL PITR/WAL requirements, restore-drill runbook and Prometheus alert examples.
- Pinned k6 load scenarios for public catalog, checkout provisioning and concurrent check-in; checkout load tests are explicitly blocked against production by default.

## What it does

- Multi-event discovery: home page with featured and upcoming events, `/events` browse with server-side search, category, city, date and price filters and pagination
- Event detail pages at `/events/{slug}` with server-side rendered SEO (title, description, canonical, OpenGraph, Twitter, schema.org `Event` JSON-LD), `robots.txt` and `sitemap.xml`
- Independent ticket types and inventory per event; server-authoritative prices and availability
- Razorpay or Cashfree checkout selected per event, with server-side provider-state verification, signed webhooks, reconciliation and refunds
- PostgreSQL row-locked reservations, idempotent checkout, unique signed QR per ticket, atomic single-use check-in
- Admin: create/update/publish/unpublish/cancel/complete/archive events, ticket type and inventory management, organizer-scoped authorization, one-time production admin setup and secure Cloudinary branding/media uploads
- Organizer operations: top-level issued-ticket console, event orders, attendee export, check-in metrics, server-enforced organizer/event scope, and explicit platform-admin → organizer owner → event manager / gate staff hierarchy.
- Customer ticket actions: native Share where supported, secure-link clipboard fallback, and Save as PDF browser flow with ticket-only print styling.
- Staff phone scanner, attendee CSV, audit log, structured logs, correlation IDs, Prometheus metrics and alert rules

## Architecture

```text
Cloudflare Tunnel: events.neelastack.com
        |
        v
127.0.0.1:4002  (host, loopback only)
        |
   edge (nginx :8080)
     |-- /api/*, /robots.txt, /sitemap.xml --> backend (Spring Boot :8080) --> PostgreSQL, Redis
     |-- everything else ---------------------> web (Angular SSR, Node :3000) --> backend (private network)
```

Ports on the Oracle VM: 4000 and 4001 belong to other applications and are never touched. This stack binds only `127.0.0.1:4002`.
Production directory: `/home/ubuntu/apps/lakhdatar-events` (never `/opt`).

## Public API (no authentication)

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/public/events` | Paginated list. Filters: `q, category, city, organizer, featured, from, to, minPrice, maxPrice, page, size` |
| `GET /api/v1/public/events/featured` `?limit=` | Featured events |
| `GET /api/v1/public/events/upcoming` | Upcoming events, soonest first |
| `GET /api/v1/public/events/search?q=` | Text search |
| `GET /api/v1/public/events/facets` | Categories and cities that currently have events |
| `GET /api/v1/public/events/{slug}` | Full event detail (published, cancelled and completed only) |

Only `PUBLISHED` events are listed. `DRAFT`, `UNPUBLISHED` and `ARCHIVED` events return 404. Sold-out, booking-not-started and
booking-closed are derived states, computed from live inventory and the booking window, so they cannot drift.

## First production administrator setup

Production must keep `BOOTSTRAP_ENABLED=false`. For a fresh deployment, set `INITIAL_ADMIN_SETUP_ENABLED=true` and a unique `INITIAL_ADMIN_SETUP_TOKEN` of at least 32 bytes in the VM `.env`, deploy, then open `/setup/initial-admin` or call the setup endpoint. The setup creates the first `ADMIN` account and an `OWNER` membership for the first organizer, but never creates a sample event. The database marks setup complete under a row lock, so the endpoint cannot be reused even if the environment flag remains enabled. After successful provisioning, set `INITIAL_ADMIN_SETUP_ENABLED=false` and redeploy.

After setup, add organizers (name + logo) under **Admin console -> Organizers**; logos are uploaded to Cloudinary and their HTTPS URL plus provider public ID are stored so replacements can be compensated and old assets reclaimed safely. The Neelastack logo is the file `frontend/src/assets/neelastack-logo.png`. Full steps: `ADMIN-SETUP-GUIDE.md`.

Configure Cloudinary only when branding uploads are required: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_FOLDER`, and `CLOUDINARY_MAX_BYTES`. The API secret remains backend-only.

## Local development

```bash
cp .env.example .env      # fill the secrets
docker compose up --build
# open http://localhost:4002
# Development uses isolated containers/volumes and will not attach to production data.
```

Backend tests: `cd backend && mvn -B verify` (integration tests need Docker; `mvn verify -Punit` skips them).
Frontend: use Node 24 (`.nvmrc` and production image baseline), refresh the lock once with `./scripts/refresh-frontend-lock.ps1`, then `cd frontend && npm ci && npm run build`; run the built SSR server with `PORT=3000 SSR_API_BASE_URL=http://localhost:8081/api/v1 node dist/frontend/server/server.mjs`.

## CI/CD

The repository intentionally uses two GitHub Actions workflows: `CI` for build, test, security and weekly security review, and `Production` for approved deployment or manual rollback. Production deploys exact immutable Git SHAs and never performs an automatic database downgrade after Flyway has run.

## Documentation

- [docs/multi-event.md](docs/multi-event.md): data model, lifecycle, SEO, event-scoped manager access
- [docs/NEELASTACK-VERSION-BASELINE.md](docs/NEELASTACK-VERSION-BASELINE.md): exact framework/runtime baseline and lockfile refresh procedure
- [PHASE5-VALIDATION.md](PHASE5-VALIDATION.md): release validation and environment limitations
- [docs/deployment.md](docs/deployment.md): Oracle VM, Cloudflare Tunnel, CI/CD, rollback
- [docs/architecture.md](docs/architecture.md), [docs/security.md](docs/security.md), [docs/payments.md](docs/payments.md), [docs/checkin.md](docs/checkin.md), [docs/backup-restore.md](docs/backup-restore.md), [docs/production-checklist.md](docs/production-checklist.md)


## Current release
**v2.0.15** is the current release. See `CHANGES-2.0.15.md`, `VALIDATION-2.0.15.md`, and `RELEASE-MANIFEST.txt`. Historical v1.9.x and v2.0.x validation records are retained for traceability.


### Deployment dotenv safety
`infra/deploy/deploy.sh` intentionally does not source `.env`; Docker Compose parses the dotenv file so values such as `MAIL_FROM="Neelastack Events <events@neelastack.com>"` cannot break Bash deployment.


## Enterprise qualification

Use `docs/ENTERPRISE-RELEASE-QUALIFICATION.md`, `docs/HA-FAILOVER-DRILL.md`, `docs/PAYMENT-CHAOS-QUALIFICATION.md`, and `.github/workflows/enterprise-release-qualification.yml` as the release gates. A literal BookMyShow-equivalent SLA requires independent HA infrastructure, provider contracts, observability and executed load/DR evidence outside this source package.

## High-concurrency production profile

The edge terminates the Cloudflare tunnel, reuses upstream connections, compresses responses, and micro-caches only the public event catalogue. The backend uses a bounded Tomcat request-thread pool with high connection/accept capacity, while PostgreSQL and Redis remain protected by bounded pools. The HA Compose profile is the scale-out path for sustained write-heavy traffic.

For a real “thousands of users” release gate, run `infra/loadtest/thousands.js` against staging and retain the k6 summary, CPU/memory, database, Redis and recovery-invariant evidence with the release SHA.


## Operations diagnostics

Enterprise structured logging and the secure Loki-backed Operations Center log viewer are documented in `docs/ENTERPRISE-LOGGING.md`.


## Production Monitor
The dedicated SRE surface is `https://monitor.neelastack.com/`, protected by Cloudflare Access plus platform ADMIN authorization.
