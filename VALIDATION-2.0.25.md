# Validation — 2.0.26

- Version: 2.0.26
- Test-only cryptographic properties are registered before optional PostgreSQL wiring.
- Existing V41 migration chain remains unchanged.
- No application runtime code or API contract was changed.
- Backend Maven verification must be run in the developer/CI environment as the authoritative Java/test gate.
