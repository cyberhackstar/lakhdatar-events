# Lakhdatar Events v1.9.33 — Deployment-qualification validation

## Source-level fixes applied

- V26 cursor migration corrected so it no longer references nonexistent `tickets.issued_at`.
- V28 remains aligned to `tickets.created_at`, the actual ticket persistence timestamp.
- Multi-event manager-scope contract now validates the server-side `event_manager_assignments` SQL boundary used by the dashboard while retaining the repository-backed staff boundary.
- Issued-ticket UI makes its default authorized **All events** scope explicit.
- Enterprise release qualification accepts both the literal and properly escaped production hostname form used by the k6 idempotency guard.
- Runtime/package metadata aligned to 1.9.33.

## Validation performed in the packaging sandbox

- Flyway migration inventory: **V1–V28**
- Invalid `tickets.issued_at` references: **0**
- Angular invalid `@if (... as ...)` patterns: **0**
- Issued-ticket component stylesheet malformed-selector scan: **0** remaining `}..` selector patterns.
- Neelastack platform baseline: **PASS**
- JSON/package-lock parsing: **PASS**
- YAML parsing: **PASS**
- Shell syntax validation: **PASS**
- Java/TypeScript delimiter/source checks on edited files: **PASS**
- No `.env`, `.class`, `.jar`, `target`, `dist`, `.angular`, or `node_modules` artifacts in the release tree.
- Existing backend test source count preserved; one contract test was updated to match the actual production implementation and one migration regression contract was added.

## CI evidence addressed

The supplied CI run showed:
- frontend baseline failure: `iOS input zoom guard missing`;
- backend test failure caused by Flyway V26 `tickets.issued_at` reference;
- contract failures in `MultiEventContractTest`, `OrganizerTicketScopeContractTest`, and `EnterpriseReleaseQualificationContractTest`;
- repeated application-context errors in catalog/concurrency tests cascading from the V26 migration failure.

The v1.9.33 source contains the iOS form-font guard required by the baseline verifier, and the release artifact re-validates it before packaging.

## Build execution note

The packaging sandbox does not have Maven/Angular dependencies cached sufficiently for a fresh full `mvn clean verify` + `npm ci && npm run build`. The artifact therefore records static/source validation honestly rather than claiming a build that was not executed here.

Mandatory CI gate before production:
- `cd backend && mvn -B -ntp clean verify`
- `cd frontend && npm ci --no-audit --no-fund && npm run build && npm test`
