-- V19: organizer-owned team.
-- Staff and event managers belong to exactly one organizer. We reuse organizer_members
-- (roles STAFF / EVENT_MANAGER) instead of adding users.organizer_id: assignManager already
-- writes EVENT_MANAGER rows there, EventAccessService.canManage ignores those roles (so they
-- confer no organizer-level privilege), and users stays a pure identity table.

ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- One-time, single-use invite links. Only the SHA-256 of the token is stored.
CREATE TABLE IF NOT EXISTS user_invites (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_by  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_user_invites_user ON user_invites (user_id);

-- Backfill: a STAFF account assigned to gates of exactly one organizer's events joins that organizer.
-- Accounts whose assignments span several organizers are ambiguous and are left unlinked
-- (their existing gate assignments keep working); an ADMIN must attach them from the console.
INSERT INTO organizer_members (organizer_id, user_id, role)
SELECT MIN(e.organizer_id), es.user_id, 'STAFF'
FROM event_staff es
JOIN events e ON e.id = es.event_id
JOIN users u ON u.id = es.user_id AND u.role = 'STAFF'
WHERE NOT EXISTS (SELECT 1 FROM organizer_members m WHERE m.user_id = es.user_id)
GROUP BY es.user_id
HAVING COUNT(DISTINCT e.organizer_id) = 1;

-- Same for EVENT_MANAGER accounts that have no membership yet.
INSERT INTO organizer_members (organizer_id, user_id, role)
SELECT MIN(e.organizer_id), a.user_id, 'EVENT_MANAGER'
FROM event_manager_assignments a
JOIN events e ON e.id = a.event_id
JOIN users u ON u.id = a.user_id AND u.role = 'EVENT_MANAGER'
WHERE NOT EXISTS (SELECT 1 FROM organizer_members m WHERE m.user_id = a.user_id)
GROUP BY a.user_id
HAVING COUNT(DISTINCT e.organizer_id) = 1;

-- Before enforcing "one organizer per member", collapse historical duplicates (assignManager used to
-- add a membership per event organizer). Keep the earliest membership; event assignments are untouched.
DELETE FROM organizer_members m
USING (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY id) AS rn
    FROM organizer_members
    WHERE role IN ('STAFF', 'EVENT_MANAGER')
) d
WHERE m.id = d.id AND d.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_organizer_member_single_team_org
    ON organizer_members (user_id)
    WHERE role IN ('STAFF', 'EVENT_MANAGER');

CREATE INDEX IF NOT EXISTS idx_organizer_members_org_role ON organizer_members (organizer_id, role);
