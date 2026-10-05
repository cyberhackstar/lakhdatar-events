# v2.0.5 Validation Record

## Scope

Dedicated separation of the business Operations page from the production SRE Monitor.

## Static validation performed in this workspace

- `VERSION`, Maven project, Angular package and package-lock are aligned to `2.0.5` after release metadata update.
- Monitor route exists at `/monitor` and is protected by `platformAdminGuard`.
- Business Operations remains at `/admin/operations` and no longer embeds the live-log UI.
- NGINX and HA NGINX redirect `monitor.neelastack.com/` to relative `/monitor`.
- NGINX and HA NGINX expose `/monitor` only when the Host is `monitor.neelastack.com`.
- NGINX and HA NGINX return 404 for `/admin/*` on `monitor.neelastack.com`, keeping business console HTML on `events.neelastack.com`.
- Existing backend `/api/v1/admin/ops/*` remains ADMIN-only and `no-store`.
- Operations log polling remains bounded at the edge and in the backend.

## Runtime release gates still required in the deployment environment

- `mvn -B -ntp clean verify`
- `npm ci --no-audit --no-fund && npm run build`
- Docker image build/startup and NGINX `-t`
- Cloudflare Access verification for `monitor.neelastack.com`
- Browser verification:
  - `https://monitor.neelastack.com/` → `https://monitor.neelastack.com/monitor`
  - ADMIN login → dedicated Production Monitor
  - `https://events.neelastack.com/admin/operations` → Business Operations
  - `https://events.neelastack.com/monitor` → 404 at edge
  - `https://monitor.neelastack.com/admin/operations` → 404 at edge
