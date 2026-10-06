# Neelastack Events v2.0.14

This release is the enterprise-hardening continuation of v2.0.8. It addresses financial recovery, event-state concurrency, large-event cancellation, payment webhook durability, privileged MFA recovery, disaster-recovery validation, deployment safety, and release smoke/load qualification.

See `CHANGES-2.0.14.md` and `VALIDATION-2.0.14.md` for the release evidence and operational gates.

## 2.0.14
- Event-cancellation refunds are retryable after failed provider attempts and terminal manual-review states are explicit.
- Event cancellation uses set-based ticket/reservation updates and the final check-in write is conditioned on the event remaining published.
- Razorpay and Cashfree webhooks are durably persisted, asynchronously processed, provider-scoped, retried with jittered backoff, and dead-lettered after bounded attempts.
- Privileged MFA is enforced when configured, with encrypted TOTP secrets, one-time DB challenges, password recovery, and an ADMIN-only emergency MFA reset path.
- Production deployment requires explicit acknowledgement for the single-node topology; the HA topology remains the enterprise availability target.
- Release smoke testing can exercise an issued ticket and PDF read path without mutating production state.
- The staging enterprise load gate now requires critical-path credentials and passes configured 1,000-user settings into k6.


## Certification boundary

The source package hardens the application for enterprise qualification. Production certification still requires target-environment execution of CI, browser E2E, staging load, payment-provider chaos, remote backup/restore and independent HA failover evidence.
