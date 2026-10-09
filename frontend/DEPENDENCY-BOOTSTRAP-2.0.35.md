# Frontend dependency bootstrap — v2.0.35

The v2.0.34 bootstrap failed on Windows before npm started because it attempted to spawn `npm.cmd` with `shell: false`.

v2.0.35 launches npm's JS CLI through Node whenever possible and retains a controlled Windows `cmd.exe` fallback. This removes the `spawnSync npm.cmd EINVAL` failure mode without weakening dependency resolution.

## Commands

```powershell
cd frontend
node --version
npm --version
npm run bootstrap:dependencies
```

Then, only after bootstrap reports `PASS`:

```powershell
npm run doctor:dependencies
npm run verify:dependencies
npm audit --audit-level=high
npm audit --omit=dev --audit-level=high
npm run build
npm test
```
