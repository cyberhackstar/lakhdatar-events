# Neelastack Events v1.9.41 — Production deployment

Deploy from the repository root on the production VM. Keep the existing PostgreSQL/Redis named volumes. Do not remove existing data volumes.

## Required release order

1. Publish the v1.9.41 images/artifacts through the normal GitHub Actions pipeline.
2. On the VM, pull the v1.9.41 images and stop/recreate the application containers using the existing `.env` secrets.
3. Start PostgreSQL first, then the backend. Flyway must reach V30 before the public frontend is switched to the new release.
4. Start the edge/SSR/frontend services and verify the production health endpoint.
5. Verify a multi-day event whose end is after its start: booking must remain open until the effective booking end (defaulting to event end).
6. Verify a real payment in the provider's production mode using a small controlled test where appropriate, and confirm the backend marks the order only after provider verification.
7. Verify scanner: accepted scan, duplicate scan, wrong-event scan, and a multi-ticket order showing `ticket X of Y` and `Y seats booked`.
8. Verify CSV export in Chrome and mobile Safari/Chrome; the release uses a native same-origin browser download rather than Angular XHR/blob handling.
9. Verify Finance ledger loads with an empty filter and with a cursor before announcing the release.

## Rollback

Keep the previous application image tag available. If a post-deploy smoke test fails, restore the previous image while preserving database volumes. Do not roll back Flyway schema by deleting or manually editing migration history.
