# v2.0.22 — Certification Gate Regression Fixes

## Safety-first scope
- Preserves the v2.0.21 application/payment API and runtime behavior.
- Fixes the invalid PostgreSQL validation statement in V33 so clean Flyway initialization can complete.
- Aligns release contract tests with the current streaming export, inventory-lock, durable mail-queue, and release-path implementations.
- Refreshes active release/version metadata and runtime defaults to 2.0.22.
- Adds a focused migration contract preventing the V33 syntax regression from returning.

## No intentional business-flow changes
- No payment lifecycle semantics changed.
- No public API endpoint was changed.
- No new database migration was introduced; V33 itself is corrected because the previous script could not complete on a clean database.
