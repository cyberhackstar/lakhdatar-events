# Production Deploy — v1.9.45

1. Build and publish the v1.9.45 backend/frontend images.
2. Deploy using the normal production compose/deploy pipeline.
3. Confirm the backend reports application version `1.9.45`.
4. Purge Cloudflare cache after the frontend image is live.
5. Hard refresh the admin/public browser once after deployment.
6. Smoke-test checkout form entry, payment cancellation/return, recovery, CSV export, event creation, and staff scanning.

The backend must remain private on container port 8080; browser requests must stay on the public same-origin `/api/v1` path.


## Post-deploy smoke checks
1. Open checkout and type in name/email/phone; entered text must remain visible.
2. Complete a Cashfree payment and confirm that a webhook-first race still lands on the confirmed ticket page without a 409.
3. Cancel a payment and confirm the result page does not force recovery.
4. Open Recovery with either Neelastack order number or provider transaction ID plus checkout email; the loading state must resolve or timeout visibly.
5. From Admin -> Event operations/list, download attendee CSV and confirm the browser receives the file.
6. Create an event via POST `/api/v1/admin/events` and confirm there is no edge redirect.
