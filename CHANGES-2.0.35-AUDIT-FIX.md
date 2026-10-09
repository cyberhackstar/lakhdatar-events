# 2.0.35 audit + CI-baseline fix

1. package.json: override tinypool 2.2.0 (critical RCE advisories); @angular/ssr 20.3.33 -> 20.3.39.
2. spec files: import 'zone.js' + 'zone.js/testing' (fixes NG0908; repo baseline requires this).
3. tools/verify-frontend-lock.mjs + tools/verify-platform-baseline.mjs: updated pins; express/@types/express exact pins accepted; void-elements check only if present.
4. package-lock.json: generated, `npm ci` verified.

Verified: bootstrap PASS, verify:baseline PASS, verify:dependencies PASS, audit:high exit 0, audit:runtime 0, 12/12 tests, production build, dist checks, SSR smoke test.
