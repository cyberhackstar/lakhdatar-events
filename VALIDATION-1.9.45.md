# Validation — v1.9.45

- Production browser API base remains same-origin `/api/v1`.
- Checkout and recovery controls use page-appropriate color schemes rather than the former global dark scheme.
- Checkout required-field semantics and phone pattern validation are present.
- Recovery required-field semantics and accessible invalid states are present.
- Primary Neelastack header mark is 48px on desktop surfaces.
- v1.9.44 authenticated CSV download path remains intact.
- Source/config JSON consistency and baseline verification are required before release.
- Full Maven/Angular GitHub Actions CI remains the authoritative final gate.


## Final hardening checks
- Source assertion: confirmed orders return idempotently from checkout verification before provider state transition.
- Source assertion: checkout fields use light color scheme with explicit foreground text.
- Source assertion: shared site header uses a 48px Neelastack brand mark.
- Source assertion: admin attendee CSV uses the authenticated ApiService/blob download path.

- Source assertion: login fields enforce email/password constraints and mark invalid controls for assistive technology.
- Source assertion: transient payment-return verification errors preserve the stored Neelastack recovery order reference.
