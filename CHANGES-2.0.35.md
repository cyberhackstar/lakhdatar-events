# Neelastack Events v2.0.35 — Windows dependency bootstrap correction

## Root cause fixed
The v2.0.34 frontend bootstrap attempted to execute `npm.cmd` through Node `spawnSync()` with `shell: false`. On Windows this can fail before npm starts, producing `spawnSync npm.cmd EINVAL`.

## Correction
- Launch npm's JavaScript CLI directly through the active Node runtime when `npm_execpath` is available.
- Fall back to the Node installation's bundled `npm-cli.js` before using a controlled `cmd.exe` fallback.
- Keep strict peer dependency resolution; no `--force` and no `--legacy-peer-deps`.
- Preserve lockfile rollback semantics if dependency resolution fails.
- Correct the dependency doctor to use the same Windows-safe npm invocation path.

## Scope
No application runtime, payment, database, QR, authentication, or API behavior was changed by this release. This release fixes the frontend dependency bootstrap path that prevented the previously intended package-lock generation on Windows.
