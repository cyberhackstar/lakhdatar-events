-- Persist provider public IDs alongside HTTPS URLs so replacements can safely clean up old assets.
-- All columns are nullable for compatibility with URLs created before v1.9.23.
ALTER TABLE organizers ADD COLUMN IF NOT EXISTS logo_public_id VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS cover_image_public_id VARCHAR(255);
ALTER TABLE brand_configurations ADD COLUMN IF NOT EXISTS organizer_logo_public_id VARCHAR(255);
ALTER TABLE brand_configurations ADD COLUMN IF NOT EXISTS event_logo_public_id VARCHAR(255);
ALTER TABLE brand_configurations ADD COLUMN IF NOT EXISTS event_banner_public_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_organizers_logo_public_id ON organizers(logo_public_id) WHERE logo_public_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_events_cover_image_public_id ON events(cover_image_public_id) WHERE cover_image_public_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_brand_config_asset_public_ids
    ON brand_configurations(organizer_logo_public_id, event_logo_public_id, event_banner_public_id);
