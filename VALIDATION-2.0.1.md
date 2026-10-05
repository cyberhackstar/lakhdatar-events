# v2.0.1 Validation

## Scope

Operations hostname routing and production configuration hardening only. Existing payment, ticket, check-in, refund and authentication write paths were not modified.

## Static checks

- ZIP extraction: PASS
- Required monitor hostname present in Cloudflare example: PASS
- Required monitor hostname present in production SSR allow-list default: PASS
- Dedicated monitor root redirect present: PASS
- Edge remains loopback-only in production compose: PASS
- No public Prometheus/Grafana/Loki/Tempo route added: PASS
- Refresh-cookie domain was not broadened: PASS
- JSON/YAML parsing: run by release validation
- Shell syntax: run by release validation

## External deployment checks

Must be performed after deployment:

1. Add `monitor.neelastack.com` Published Application route to the existing Cloudflare Tunnel, targeting `http://localhost:4002`.
2. Create Cloudflare Access Self-hosted application and explicit operator Allow policy.
3. Verify `https://monitor.neelastack.com/` redirects to `/admin/operations` after Access authentication.
4. Verify non-operator identity is denied by Cloudflare Access.
5. Verify an operator without the application `ADMIN` role cannot access the Operations API.
6. Verify Prometheus/Grafana/Loki/Tempo remain unreachable from the public Internet.
7. Verify direct VM edge access is loopback-only.
