# Production Deployment — 1.9.48

1. Deploy the 1.9.48 images/source using the normal production deployment procedure.
2. Confirm the backend reports application version `1.9.48`.
3. Confirm the edge proxies `/api/v1` to `backend:8080` and does not expose a public backend port.
4. Purge Cloudflare cache after frontend deployment and perform one hard refresh.
5. Smoke test: admin event creation, authenticated attendee CSV export, Cashfree success/pending/cancel return, recovery by order number/provider payment ID, and ticket PDF download.
6. Monitor `/api/v1/webhooks/cashfree`, `/api/v1/public/checkout/verify`, and admin export responses for the first production checkout cycle.

Do not bypass authentication or downgrade HTTPS to work around client-side errors.
