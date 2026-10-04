# v1.9.25 — Production release hardening

This release closes the v1.9.24 production blockers identified during the repository-wide audit.

## Fixed
- Public sitemap shards (`/sitemap-{page}.xml`) are explicitly permitted by Spring Security, matching the edge and SEO controller routes.
- Sitemap shard requests now reject page numbers beyond the published-event shard count, preventing pathological database offsets.
- Event publish now acquires the pessimistic parent-event row lock before transition validation and re-checks authorization after locking.
- Event update now acquires the same parent-event row lock used by checkout and inventory mutations, preventing concurrent administrative updates from racing with inventory/lifecycle changes.
- Added regression contract coverage for sitemap reachability/bounds and event publish/update locking.
- Version metadata is aligned at 1.9.25.

## Compatibility
- No existing test files were modified or removed.
- No public API payload contract was intentionally changed.
- Existing Flyway migrations remain immutable; this release does not introduce a database schema change.
