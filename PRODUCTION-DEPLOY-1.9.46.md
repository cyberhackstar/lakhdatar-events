# Production Deploy — v1.9.46

1. Build/publish the backend and frontend images from the v1.9.46 source tree.
2. Deploy using the normal immutable image-tag workflow; do not rebuild the production image directly on the VM.
3. Confirm the backend reports application version `1.9.46`.
4. Confirm the edge serves the new frontend bundle and `/api/v1` remains same-origin.
5. Purge the Cloudflare cache or wait for the new hashed frontend bundle to be requested, then hard-refresh one admin and one public browser session.
6. Smoke-test event creation, attendee CSV export, checkout, Cashfree cancellation/return, recovery, login, and ticket display.
7. Treat GitHub Actions Maven + Angular CI as the final release gate.
