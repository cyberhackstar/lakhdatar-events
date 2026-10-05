# Neelastack Events v2.0.5 — Dedicated SRE Monitor

## Operator surface separation

- `events.neelastack.com` keeps the organizer/admin business workspace and now labels `/admin/operations` as **Business Operations**.
- `monitor.neelastack.com` is now a distinct **Production Monitor / SRE Control Plane** at `/monitor`.
- The monitor hostname root now uses a relative canonical redirect to `/monitor`, so the browser never receives the private `:8080` origin port.
- The `/monitor` route is restricted to platform `ADMIN` users by Angular routing and is edge-gated to `monitor.neelastack.com`; the same route is not exposed on `events.neelastack.com`.
- Business `/admin/*` pages are also blocked on the monitor hostname, so the monitor surface cannot silently fall back into the organizer console.
- The monitor surface is intentionally focused on infrastructure diagnosis, incident/recovery queues, searchable live logs and safe operator guidance rather than organizer workflows.
- Live logs remain bounded, redacted and served through the authenticated backend BFF; Loki itself stays private.

## Security boundary

- Keep Cloudflare Access in front of `monitor.neelastack.com` with an explicit Allow policy for approved operator identities and deny-by-default behavior.
- Keep the application-level `ADMIN` authorization check in place even when Cloudflare Access is enabled.
- Keep the refresh cookie host-only; do not widen it to `.neelastack.com` just to share sessions between the business and SRE hostnames.
