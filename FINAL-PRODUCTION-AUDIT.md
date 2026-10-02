## v1.9.13 Alpine security corrective pass

The v1.9.13 hotfix adds Alpine package upgrades to the frontend and edge image builds. The latest CI run reached registry-backed Trivy successfully and showed the backend image clean, while the frontend image was blocked by four HIGH OpenSSL findings on Alpine 3.22.4. The fixed versions are available in the Alpine v3.22 repository.

# Final Production Audit — 1.9.13

## Release status

1.9.12 is the CI/CD follow-up that hardens multi-architecture image publication and registry-backed Trivy scanning, following v1.9.10 edge non-root hardening. The objective is to keep the repository deployable with deterministic dependency metadata, consolidated automation, and a non-root public edge.

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

- Release metadata: 1.9.12
- JSON lockfile parse: PASS
- Required workflow count: 2
- Invalid Trivy references: 0
- Legacy workflow references: 0
- `etag@1.9.0` lockfile references: 0
- Flyway sequence remains V1..V17

## External verification boundary

The repository's Maven backend build was previously verified by the user's Windows environment through v1.9.7 (`76 tests, 0 failures, 0 errors, BUILD SUCCESS`). The v1.9.11 changes are focused on multi-architecture image publication and registry-backed Trivy scanning, while retaining the v1.9.10 edge container hardening and frontend dependency security metadata.


### v1.9.9 CI Security Recheck (historical)
- npm override: piscina 5.3.2
- Angular application remains 20.3.x
- No Angular-major upgrade introduced solely to satisfy the advisory.
- CI workflow count: 2 (CI, Production).


### v1.9.10 Edge Hardening Recheck
- `edge/Dockerfile` contains an explicit non-root `USER nginx`.
- NGINX listens on container port `8080`; the VM public entry remains `127.0.0.1:4002`.
- Production edge no longer adds `NET_BIND_SERVICE`.
- NGINX cache tmpfs is explicitly owned by UID/GID `101:101`; `/tmp` is a bounded runtime tmpfs for the PID file.
- Static Compose/YAML parsing completed successfully in the packaging environment.
- Docker build/runtime execution could not be performed in this sandbox because the Docker CLI/daemon is not installed.

## v1.9.11 CI follow-up

The Docker release pipeline now publishes multi-architecture `linux/amd64,linux/arm64` images. This resolves Trivy failures caused by scanning ARM64-only registry manifests from AMD64 GitHub-hosted runners while retaining native ARM64 deployment for the Oracle VM.



## v1.9.12 corrective pass

The first v1.9.12 package was not suitable for CI because release metadata was only partially bumped. This corrected release updates all version-bearing files and preserves the Testcontainers BOM from the verified v1.9.11 baseline. CI's Gitleaks step now uses full history, and the unsupported GitHub Dependency Review job has been removed rather than making deployment depend on a repository setting that is currently disabled.

Backend runtime dependency overrides are pinned to security-patched patch versions identified by the registry-backed Trivy scan: Jackson 2.21.7 / 3.1.7, HttpComponents Core5 5.4.3 and Tomcat 11.0.26.
