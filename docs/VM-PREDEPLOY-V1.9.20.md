# v1.9.20 first-production VM preflight

This release keeps the Lakhdatar stack isolated from every other application on the Oracle VM. The only host port owned by this stack is `127.0.0.1:4002`; do not run broad Docker prune commands.

## Before deployment

Run from the production directory:

```bash
cd /home/ubuntu/apps/lakhdatar-events

printf '\n=== Lakhdatar containers ===\n'
docker ps -a --filter name=lakhdatar-

printf '\n=== Lakhdatar edge port ===\n'
sudo ss -lntp | grep ':4002' || true

printf '\n=== Production Compose validation ===\n'
IMAGE_TAG=0000000000000000000000000000000000000000 docker compose --env-file .env -f infra/docker-compose.prod.yml config >/dev/null && echo 'Compose config: PASS'

printf '\n=== Current service state ===\n'
docker inspect --format='{{.Name}} state={{.State.Status}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
  lakhdatar-postgres lakhdatar-redis lakhdatar-backend lakhdatar-web lakhdatar-edge 2>/dev/null || true
```

The `config` check only validates Compose interpolation; it does not start or stop anything.

Do not delete PostgreSQL/Redis volumes, `.env`, backups, or any container/image not named `lakhdatar-*`. Do not use `docker system prune -a`, `docker image prune -a`, or `docker volume prune`.

The deployment pipeline performs a verified PostgreSQL backup before pulling the immutable release.

## Optional first-live cleanup

After the new release is successfully live, inspect old Lakhdatar-only images:

```bash
docker image ls --format '{{.Repository}}:{{.Tag}}\t{{.ID}}' | grep '^ghcr.io/cyberhackstar/lakhdatar-' || true
```

Remove only an explicitly identified old Lakhdatar image, for example the obsolete v1.9.18 edge image:

```bash
docker image rm ghcr.io/cyberhackstar/lakhdatar-edge:4886837b7f8a508c1bb2021630d123115594f8da || true
```

If Docker reports that an image is still referenced by a container, leave it in place; it is safer to retain it until that container is replaced by a successful release.
