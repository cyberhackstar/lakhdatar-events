-- V11: event-scoped manager assignments.
-- EVENT_MANAGER users may only operate events explicitly assigned to them.
CREATE TABLE event_manager_assignments (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_event_manager_assignment UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_manager_assignment_user_event
    ON event_manager_assignments (user_id, event_id);
CREATE INDEX idx_event_manager_assignment_event_user
    ON event_manager_assignments (event_id, user_id);

-- Existing EVENT_MANAGER accounts were previously organizer-wide. There is no safe
-- way to infer a single event from historical data, so assignments intentionally
-- start empty. An ADMIN/ORGANIZER must explicitly assign managers to events.
