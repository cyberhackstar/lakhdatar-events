CREATE TABLE IF NOT EXISTS event_notification_jobs (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    kind VARCHAR(32) NOT NULL,
    change_key VARCHAR(128) NOT NULL,
    old_starts_at TIMESTAMPTZ,
    old_ends_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ,
    CONSTRAINT chk_event_notification_kind CHECK (kind IN ('CANCELLED', 'DETAILS_CHANGED')),
    CONSTRAINT chk_event_notification_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_event_notification_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT uk_event_notification_change UNIQUE (event_id, order_id, kind, change_key)
);

CREATE INDEX IF NOT EXISTS idx_event_notification_due
    ON event_notification_jobs(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_event_notification_event
    ON event_notification_jobs(event_id, order_id);
