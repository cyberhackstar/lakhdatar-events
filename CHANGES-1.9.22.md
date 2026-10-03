# CHANGES 1.9.22

## Backend
- `service/OrganizerAdminService.java` (new): list/create organizers; validates name, slug, website; uploads logo before the DB write; audit-logged `ORGANIZER_CREATED`.
- `controller/AdminController.java`: `GET /admin/organizers`, `POST /admin/organizers` (multipart: name, slug, description, website, logo).
- `service/CloudinaryAssetService.java`: extracted `validatedImageBytes`, `uploadBytes`, public `storeImage`, `isConfigured`. Same limits (JPEG/PNG, size cap, 8000px, 25MP). Existing `upload` behaviour unchanged.
- `service/BrandService.java`: no default organizer logo; technology-partner logo always the bundled platform logo.
- `service/EventManagementService.java`, `service/BootstrapData.java`: no Lakhdatar logo fallback.
- `application.yml`: fixed `neelastack-logo-url` (png), empty default organizer logo.
- `db/migration/V18__platform_logo_png_and_no_default_organizer_logo.sql`: rewrites old svg paths.
- `security/JwtService.java`: error text names `JWT_SECRET`.
- `RazorpaySignatureTest.java`: Branding constructor values updated.

## Frontend
- `features/admin/organizers.component.ts` (new) and route `/admin/organizers`.
- `admin.component.ts`: Organizers nav link (ADMIN), organizer selector, `organizerSlug` sent on create.
- `api.service.ts`, `api.models.ts`: organizer calls/types.
- `initial-admin.component.ts`: no prefilled Lakhdatar values.
- `co-branded-header.component.ts`: no broken image when no logo.
- `server.ts`: 10-minute cache for `/assets`.
- Logo references svg -> png (header, login, admin, staff, favicon); SVG assets deleted; placeholder `neelastack-logo.png` added.

## Infra / docs
- `infra/docker-compose.prod.yml`, `docker-compose.yml`, `.env.example` wiring fixes.
- New: `ADMIN-SETUP-GUIDE.md`, `ENV-LINKAGE-AUDIT.md`.
- Version bump to 1.9.22.

## Review follow-ups (post-review patch, same version)
- `exception/GlobalExceptionHandler.java`: `MultipartException` handler; uploads over the multipart limit now return 413 `IMAGE_TOO_LARGE` instead of a generic 500.
- `infra/docker-compose.prod.yml`: Redis password is read from the container environment (not placed on the command line); Razorpay vars default to empty so a Cashfree-only deployment gets no "variable not set" warnings.
- `organizers.component.ts`: stale success message cleared when a new file error is shown.
- New test `UploadErrorHandlingContractTest`.
