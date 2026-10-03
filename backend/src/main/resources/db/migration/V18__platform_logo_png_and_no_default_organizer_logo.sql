-- V18: the Neelastack platform mark is now a PNG, and organizers no longer have a built-in default logo.
-- Organizer name + logo are entered (Cloudinary upload) when the organizer is created.
-- Only rows that still hold the old bundled placeholder paths are touched; any Cloudinary URL is left intact.

UPDATE brand_configurations
   SET technology_partner_logo_url = '/assets/neelastack-logo.png'
 WHERE technology_partner_logo_url = '/assets/neelastack-logo.svg';

UPDATE brand_configurations
   SET organizer_logo_url = NULL
 WHERE organizer_logo_url IN ('/assets/lakhdatar-logo.svg', '');

UPDATE organizers
   SET logo_url = NULL
 WHERE logo_url IN ('/assets/lakhdatar-logo.svg', '');
