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

## What it does

- Multi-event discovery: home page with featured and upcoming events, `/events` browse with server-side search, category, city, date and price filters and pagination
- Event detail pages at `/events/{slug}` with server-side rendered SEO (title, description, canonical, OpenGraph, Twitter, schema.org `Event` JSON-LD), `robots.txt` and `sitemap.xml`
- Independent ticket types and inventory per event; server-authoritative prices and availability
- Razorpay checkout with server-side signature and provider-state verification, webhooks, reconciliation and refunds
- PostgreSQL row-locked reservations, idempotent checkout, unique signed QR per ticket, atomic single-use check-in
- Admin: create/update/publish/unpublish/cancel/complete/archive events, ticket type and inventory management, organizer-scoped authorization
- Staff phone scanner, attendee CSV, audit log, structured logs, correlation IDs, Prometheus metrics and alert rules

## Architecture

```text
Cloudflare Tunnel: events.neelastack.com
        |
        v
127.0.0.1:4002  (host, loopback only)
        |
   edge (nginx :80)
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

## Local development

```bash
cp .env.example .env      # fill the secrets
docker compose up --build
# open http://localhost:4002
```

Backend tests: `cd backend && mvn -B verify` (integration tests need Docker; `mvn verify -Punit` skips them).
Frontend: use Node 22, refresh the lock once with `./scripts/refresh-frontend-lock.ps1`, then `cd frontend && npm ci && npm run build`; run the built SSR server with `PORT=3000 SSR_API_BASE_URL=http://localhost:8081/api/v1 node dist/frontend/server/server.mjs`.

## Documentation

- [docs/multi-event.md](docs/multi-event.md): data model, lifecycle, SEO, event-scoped manager access
- [docs/NEELASTACK-VERSION-BASELINE.md](docs/NEELASTACK-VERSION-BASELINE.md): exact framework/runtime baseline and lockfile refresh procedure
- [PHASE5-VALIDATION.md](PHASE5-VALIDATION.md): release validation and environment limitations
- [docs/deployment.md](docs/deployment.md): Oracle VM, Cloudflare Tunnel, CI/CD, rollback
- [docs/architecture.md](docs/architecture.md), [docs/security.md](docs/security.md), [docs/payments.md](docs/payments.md), [docs/checkin.md](docs/checkin.md), [docs/backup-restore.md](docs/backup-restore.md), [docs/production-checklist.md](docs/production-checklist.md)
