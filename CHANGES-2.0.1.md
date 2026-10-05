# Neelastack Events v2.0.1 — Operations Host Hardening

- Added dedicated `monitor.neelastack.com` Cloudflare Tunnel route configuration.
- Added monitor-host root redirect to `/admin/operations`.
- Added SSR host allow-list support for `monitor.neelastack.com`.
- Added production Cloudflare Access setup documentation.
- Kept observability systems private; no Prometheus/Grafana/Loki/Tempo public route added.
- Kept authentication cookies host-only; no cross-subdomain session expansion.
- No payment, ticket, inventory, check-in or refund write-path changes.
