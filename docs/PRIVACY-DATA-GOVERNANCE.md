# Privacy and data governance baseline

Classify data before production use:

- Public: event names, public event descriptions and published catalog information.
- Internal: operational metadata and non-public configuration.
- Confidential: attendee contact details, organizer data, staff assignments, audit records.
- Highly sensitive: authentication credentials, token hashes, MFA secrets, payment-provider credentials.

Controls:

- Never log passwords, bearer tokens, cookies, provider secrets or raw payment signatures.
- Store only token hashes/derivatives where practical; encrypt MFA secrets at rest.
- Keep customer ticket credentials out of query strings and analytics.
- Define retention periods for operational tables and legal/audit records.
- Restrict production database access to named operators using least privilege.
- Record deletion/export requests through an approved privacy workflow rather than direct SQL edits.

Before public launch, have the actual Privacy Policy, Terms, refund/cancellation policy and applicable Indian privacy/tax requirements reviewed by the responsible business/legal owner.
