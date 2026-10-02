-- V14: one-time production setup state and explicit branding presentation mode.
CREATE TABLE IF NOT EXISTS platform_setup_state (
    id SMALLINT PRIMARY KEY,
    initial_admin_completed_at TIMESTAMPTZ
);

INSERT INTO platform_setup_state (id, initial_admin_completed_at)
VALUES (1, NULL)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE brand_configurations
    ADD COLUMN IF NOT EXISTS branding_mode VARCHAR(16) NOT NULL DEFAULT 'BOTH';

ALTER TABLE brand_configurations
    DROP CONSTRAINT IF EXISTS chk_branding_mode;

ALTER TABLE brand_configurations
    ADD CONSTRAINT chk_branding_mode
    CHECK (branding_mode IN ('TEXT_ONLY','LOGO_ONLY','BOTH'));
