# Validation v1.9.39

Release target: production deployment.

Checks performed in packaging workspace:
- version consistency: PASS
- Flyway migration naming/order: PASS (V30 follows V29)
- source tree excludes generated build/runtime directories in final archive: PASS
- payment cookie path review: PASS (`/`)
- Cashfree CSP review: PASS (`api.cashfree.com` allowed in `connect-src`, `frame-src`, and `form-action`)
- finance ledger UUID column mapping review: PASS (provider `public_id` selected)
- attendee CSV endpoint review: PASS (byte response with content length/no-store)
- ticket order-count/position propagation review: PASS

Full CI build/test and Docker image builds must still be executed by GitHub Actions / the production runner.

Additional release checks:
- frontend ticket UI exposes ticket position + order seat count: PASS
- scanner result exposes order seat count + ticket position: PASS
- lifecycle UI blocks completion before the event end: PASS
- production deployment notes document V29/V30 migration and Cloudflare HTTP/3 fallback: PASS
