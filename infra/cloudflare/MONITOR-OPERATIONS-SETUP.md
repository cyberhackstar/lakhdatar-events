# Neelastack Events — monitor.neelastack.com Production Setup

## Purpose

`https://monitor.neelastack.com` is the dedicated read-only Neelastack Operations Center. It uses the same private edge origin as `events.neelastack.com`; it does not expose Prometheus, Grafana, Loki, Tempo, PostgreSQL or Redis.

## Cloudflare Tunnel route

Add a Published Application route to the existing tunnel:

- Hostname: `monitor.neelastack.com`
- Service: `http://localhost:4002`

Cloudflare Tunnel supports mapping a public hostname to a local service, and the route should terminate at the existing loopback-only edge. Keep the origin port unexposed to the Internet.

## Cloudflare Access — required

Create a **Self-hosted** Access application for `monitor.neelastack.com`. Use an explicit Allow policy for the small set of operator/admin identities that need access. Keep the default deny posture for everyone else.

Recommended policy:

1. Include only the Neelastack operator/admin email addresses or an approved identity-provider group.
2. Do not create a public Bypass policy.
3. Prefer a dedicated IdP/group for operators.
4. Enable a short Access session lifetime appropriate for an operations console.
5. If available for your team, add device posture/MFA requirements.
6. Enable Tunnel-side Access token protection so a request that bypasses Access is not silently trusted at the origin.

## Application authentication

The Operations Center still requires the platform's own authenticated `ADMIN` role. Cloudflare Access is the perimeter gate; Spring Security remains the application authorization gate. Do not remove either layer.

The refresh cookie intentionally remains host-only. Do **not** change it to `Domain=.neelastack.com` merely to share sessions between `events.neelastack.com` and `monitor.neelastack.com`; keeping sessions isolated reduces cross-subdomain blast radius.

## Application URL

After the route and Access application are active:

`https://monitor.neelastack.com/` → redirects to `/admin/operations`.

The user then signs in to the Neelastack platform if an application session is not already established on this hostname.

## Origin security

The edge listens on `127.0.0.1:4002` only. Do not bind the edge to `0.0.0.0`. Do not publish backend port 8080, PostgreSQL 5432, Redis 6379, Prometheus, Grafana, Loki or Tempo publicly.

## Validation

On the VM, after deploying the release:

```bash
cloudflared tunnel ingress validate
curl -I -H 'Host: monitor.neelastack.com' http://127.0.0.1:4002/
curl -fsS http://127.0.0.1:4002/edge-health
```

The direct origin check should only be used locally on the VM. Public access should go through Cloudflare Access.
