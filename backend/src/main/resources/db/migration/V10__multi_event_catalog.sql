-- V10: multi-event catalogue.
-- Neelastack is the platform owner; organizers (currently Lakhdatar Events) own events.
-- Additive only: no existing booking, payment, ticket or inventory row is modified.

-- ---------------------------------------------------------------- organizers
ALTER TABLE organizers
    ADD COLUMN IF NOT EXISTS logo_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS description   TEXT,
    ADD COLUMN IF NOT EXISTS address       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS support_hours VARCHAR(255),
    ADD COLUMN IF NOT EXISTS instagram_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS facebook_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS terms         TEXT;

-- ---------------------------------------------------------------- venues
ALTER TABLE venues
    ADD COLUMN IF NOT EXISTS state    VARCHAR(120),
    ADD COLUMN IF NOT EXISTS country  VARCHAR(120) NOT NULL DEFAULT 'India',
    ADD COLUMN IF NOT EXISTS map_url  VARCHAR(500);

-- ---------------------------------------------------------------- events
ALTER TABLE events
    ADD COLUMN IF NOT EXISTS short_description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS category          VARCHAR(60)  NOT NULL DEFAULT 'General',
    ADD COLUMN IF NOT EXISTS timezone          VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN IF NOT EXISTS cover_image_url   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS gallery_urls      TEXT,
    ADD COLUMN IF NOT EXISTS highlights        TEXT,
    ADD COLUMN IF NOT EXISTS booking_starts_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS booking_ends_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS published_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS terms             TEXT,
    ADD COLUMN IF NOT EXISTS refund_policy     TEXT,
    ADD COLUMN IF NOT EXISTS age_restriction   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS featured          BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS display_order     INTEGER      NOT NULL DEFAULT 0;

-- Existing published events keep their original creation time as publication time.
UPDATE events SET published_at = created_at WHERE status = 'PUBLISHED' AND published_at IS NULL;

-- Lifecycle now includes COMPLETED and ARCHIVED. SOLD_OUT is intentionally NOT stored:
-- it is derived from ticket_types inventory so it can never drift from the authoritative counters.
ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_event_status;
ALTER TABLE events ADD CONSTRAINT chk_event_status
    CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','CANCELLED','COMPLETED','ARCHIVED'));

ALTER TABLE events ADD CONSTRAINT chk_event_booking_window
    CHECK (booking_starts_at IS NULL OR booking_ends_at IS NULL OR booking_ends_at > booking_starts_at);

-- ---------------------------------------------------------------- indexes for listing / filtering
CREATE INDEX IF NOT EXISTS idx_events_public_listing ON events (status, starts_at);
CREATE INDEX IF NOT EXISTS idx_events_organizer_status ON events (organizer_id, status, starts_at);
CREATE INDEX IF NOT EXISTS idx_events_featured ON events (featured, display_order) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_events_category ON events (category) WHERE status = 'PUBLISHED';
CREATE INDEX IF NOT EXISTS idx_venues_city ON venues (lower(city));
