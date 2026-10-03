# IMPLEMENTATION STATUS 1.9.22

## Completed and verified
- Frontend: `npm ci` + `ng build` succeed; built SSR server starts, `/healthz` ok, `/assets/neelastack-logo.png` served with 10 min cache, `/admin/organizers` route responds.
- `node tools/verify-platform-baseline.mjs`: PASS.
- Java edits parse cleanly (tree-sitter syntax check).

## Completed but NOT executed here (run before deploying)
- Backend compile and tests: `cd backend && mvn -B verify` (Maven Central unreachable in the authoring sandbox). Risk areas: `OrganizerAdminService`, `CloudinaryAssetService` refactor, `RazorpaySignatureTest` constructor args.
- V18 migration against a real PostgreSQL (Testcontainers integration tests cover Flyway).
- Playwright/E2E, live Cloudinary upload, live payment flows.

## Not done / needs your input
- Real Neelastack logo: replace `frontend/src/assets/neelastack-logo.png` (placeholder shipped).
- Organizer logo for Lakhdatar: upload via Admin -> Organizers after deploy (V18 clears the old placeholder).
- Real production env values cannot be verified from the zip (see ENV-LINKAGE-AUDIT.md checklist).

## Known, deliberately left unchanged
- `SUPPORT_EMAIL` / `SUPPORT_PHONE` unused by any code.
- Many tuning env vars (rate limits, DB pool, timeouts) are not passed through Compose.
- Uploading an organizer logo overwrites the organizer logo on all its events' brand rows, including any per-event override.
- Frontend `siteUrl` is baked at build time.
- Cloudinary call in `CloudinaryAssetService.upload` still runs inside a DB transaction (new organizer creation does not).
