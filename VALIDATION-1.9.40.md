# Validation v1.9.40

## CI failures addressed

1. Frontend TS2769 in `event-editor.component.ts` caused by passing `string | undefined` to `new Date(...)`: corrected by strict control-flow narrowing.
2. Backend `EnterpriseScaleContractTest` `NoSuchFileException` caused by paths assuming a repository-root Maven working directory: corrected with a cwd-independent fixture resolver.
3. Angular test runtime `NG0908` risk: Zone.js and `zone.js/testing` are configured globally in the Angular unit-test builder, in addition to explicit imports in existing specs.

## Release invariants

- Release version: **1.9.40**
- Flyway migrations: V1–V30 retained.
- No generated `node_modules`, `dist`, `.angular`, `target`, or `.git` directories are included in the release archive.
- Event booking fallback remains event end, not event start, for events with an explicit end time.
- Customer and gate surfaces retain per-order ticket count and ticket position metadata.

## Verification environment note

The repository package was statically validated and inspected in this build environment. Full Maven and Angular CI execution still belongs to the GitHub Actions environment that supplies the project dependencies, browser, and container build toolchain. No CI result is represented as passed unless it was actually executed.

## Local static validation completed for packaged source

- Stability baseline: PASS (`node tools/verify-platform-baseline.mjs`)
- Frontend TypeScript transpilation: PASS, 65 files, 0 diagnostics (transpile-only; dependency-backed Angular type checking remains a CI responsibility)
- JSON configuration parsing: PASS (`package.json`, `tsconfig*.json`, `angular.json`)
- Maven POM XML parsing: PASS
- Shell syntax: PASS for deployment/backup/load-test shell scripts
- Node syntax: PASS for repository `.mjs` and infrastructure `.js` files
- Release version consistency: PASS for `VERSION`, Maven and frontend package metadata at 1.9.40
- Generated-artifact exclusion: PASS; no `node_modules`, `dist`, `.angular`, `target`, or `.git` in release tree
- Exact CI regressions fixed in source: TS2769 event editor narrowing and backend `EnterpriseScaleContractTest` frontend fixture resolution
