# Final Production Audit — v1.9.8

## Release status

v1.9.8 is the CI/CD and dependency-lock repair release following the successful v1.9.7 backend verification. The objective is to make the repository deployable with a deterministic frontend lockfile and a small, coherent automation surface.

## Confirmed fixes

1. Frontend lockfile repaired: `etag` is `1.8.1` with the official npm tarball URL and integrity metadata; all four dependency edges previously mutated to `1.9.0` were corrected.
2. Trivy Action pinned to the immutable v0.36.0 commit.
3. GitHub workflows reduced from four to two: `CI` and `Production`.
4. Weekly security scan is integrated into CI and cannot trigger production because the production workflow requires a successful `push`-origin CI run on `main`.
5. Production deploy and manual rollback are unified under `Production`.
6. Post-Flyway automatic rollback is disabled to avoid unsafe binary/schema downgrades; rollback is an explicit operator action.
7. Node 24 is now consistent across `.nvmrc`, CI and the frontend lock-refresh script.
8. CI now boots the generated Angular SSR server and checks `/healthz` plus rendered HTML before Docker images are built.

## Release verification performed in the packaging environment

- Release metadata: 1.9.8
- JSON lockfile parse: PASS
- Required workflow count: 2
- Invalid Trivy references: 0
- Legacy workflow references: 0
- `etag@1.9.0` lockfile references: 0
- Flyway sequence remains V1..V17

## External verification boundary

The repository's Maven backend build was previously verified by the user's Windows environment through v1.9.7 (`76 tests, 0 failures, 0 errors, BUILD SUCCESS`). The v1.9.8 changes are limited to CI/CD, frontend lock metadata, documentation and release contracts; the user should rerun `npm ci && npm run build` and the CI workflow for registry-backed confirmation.
