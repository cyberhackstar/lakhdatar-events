# Changes — v1.9.45

## Premium public UI and form reliability

- Fixed checkout form text visibility by removing the unsafe global dark form-control color scheme and explicitly applying a light scheme to checkout controls.
- Added explicit editable text colors, caret colors, placeholder contrast, focus rings, invalid states and mobile-safe sizing.
- Added optional-phone validation for 10-digit Indian mobile numbers when supplied.
- Added required-field semantics and accessible invalid states on checkout and ticket recovery forms.
- Bumped the Neelastack platform mark to 48px in the primary public/admin/staff/event headers.
- Upgraded checkout/recovery/payment-result branding to use the actual Neelastack mark rather than plain text-only branding.
- Improved search/filter control focus and readability across the public catalogue.
- Preserved authenticated CSV export, payment verification, recovery timeouts and production same-origin API behavior from v1.9.44.

## Scope

This release is presentation and form-reliability focused. Payment, inventory, authentication and authorization backend invariants remain unchanged.


### Final hardening pass
- Checkout verification is idempotent when a provider webhook has already confirmed the order; browser return cannot trigger a COMPLETED -> CAPTURED conflict.
- Checkout mobile phone guidance now matches the strict 10-digit validator.
- Added contract coverage for the 48px shared brand mark, checkout light color scheme/text visibility, and authenticated CSV download path.

- Hardened login inputs with client-side validation, accessible invalid states, autofill-safe text rendering, and explicit password/email constraints.
- Payment return errors now preserve the local Neelastack order number when available instead of replacing it with the provider order reference.
