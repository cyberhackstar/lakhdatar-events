# v1.9.23 Release Validation

## Completed in the packaging sandbox

- Platform baseline validator: **PASS** (`node tools/verify-platform-baseline.mjs`).
- `frontend/package.json` / `frontend/package-lock.json`: **PASS** JSON parsing and version consistency at 1.9.23.
- Project YAML: **PASS** for development Compose, production Compose, Spring configuration, CI and Production workflows.
- Shell scripts: **PASS** with `bash -n` for every `.sh` file.
- Java syntax parser: **PASS**, all 151 backend Java source/test files parsed with zero syntax diagnostics using the JDK compiler parser API.
- Existing backend test sources: **UNCHANGED byte-for-byte**. Two regression tests were added; no existing test file was modified or removed.
- Source compatibility/invariant checks: **PASS** for inventory locking, SQL capacity backstop, durable mail queue, Cloudinary transaction isolation/compensation, streaming CSV, QR cache, sitemap sharding, production knobs and recovery indexes.
- Original project files: **314 retained** with **0 removals**; release tree contains **10 additive files** (release documentation plus hardening code/migrations/tests).
- ZIP archive integrity: **PASS** (`unzip -t`).

## Network/runtime gates not executable in this sandbox

Maven and Docker are not installed here. A frontend `npm ci` attempt also hit the sandbox package-network timeout and was terminated; no partial `node_modules` directory is included in the release archive.

Therefore these release gates must still execute in the existing CI/Oracle VM environment:

```bash
cd backend && mvn -B -ntp clean verify
cd ../frontend && npm ci --no-audit --no-fund && npm run build && npm test
```

The existing CI must additionally complete the multi-architecture Docker builds, Gitleaks, CodeQL, Trivy, edge startup regression, and production public smoke test. Production smoke now covers `/`, `/api/v1/public/events/upcoming`, `/sitemap.xml`, and `/sitemap-1.xml`.

The release cannot honestly be described as live-deployment-verified without those external runtime gates. The source package has been prepared to preserve all existing tests and public API contracts while adding only forward Flyway migrations V20-V23 and targeted regression coverage.
