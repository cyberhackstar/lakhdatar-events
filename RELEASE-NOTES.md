# Neelastack Event Platform — v1.9.54

Enterprise release-qualification patch for the v1.9.53 CI failures.

The two failing tests are corrected without weakening production controls: forced-password-change protection is tested behaviorally, and the event lifecycle integration test uses a deterministic test-only payment provider.

See `CHANGES-1.9.54.md` and `VALIDATION-1.9.54.md`.
