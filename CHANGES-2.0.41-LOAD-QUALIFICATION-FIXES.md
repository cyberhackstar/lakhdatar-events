# v2.0.41 — Load qualification corrections

- Keep `catalog.js` focused on public catalogue endpoints. The prior script also repeatedly called ticket/admin APIs using one shared ticket token and admin bearer during a ramping 50-iteration/s public workload. Those endpoints are rate-limited/authorization-protected and their expected protection responses polluted the public `http_req_failed` SLO. Ticket and admin APIs remain covered by dedicated fixture-backed `ticket-pdf.js` and `operations.js` scenarios.
- Make the temporary k6 summary directory writable for the pinned image's unprivileged UID, then restore its original permissions on exit. This addresses `/results/catalog.json: permission denied` and preserves summary evidence.
- Add static regression contracts for public-only catalogue load and report permission restoration.

## Security status

The staging ZAP scan still reports alert 10055 (`CSP: style-src unsafe-inline`) as a new warning. This release deliberately does **not** waive or downgrade that security finding. A nonce/hash-based Angular SSR CSP migration requires browser/payment compatibility testing before it can be considered fixed.
