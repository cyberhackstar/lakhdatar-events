# Frontend dependency bootstrap — 2.0.34

Run from `frontend/` with Node 22.19+ (22.x) or Node 24.x.

```powershell
node --version
npm --version
npm run bootstrap:dependencies
npm run doctor:dependencies
npm run build
npm test
```

The bootstrap command first reconciles `package-lock.json` using the normal npm peer resolver, then performs a clean `npm ci`, lock/dependency verification, and HIGH/CRITICAL audits.

No `--force` and no `--legacy-peer-deps` path is used.

A release is not certifiable until the generated `frontend/package-lock.json` is committed and the CI/staging gates pass.
