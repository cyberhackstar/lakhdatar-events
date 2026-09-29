-- Ensure every scan is attributable to the event that was selected by the staff member.
ALTER TABLE ticket_checkins ADD COLUMN IF NOT EXISTS event_id BIGINT;

UPDATE ticket_checkins c
SET event_id = t.event_id
FROM tickets t
WHERE c.event_id IS NULL AND c.ticket_id = t.id;

CREATE INDEX IF NOT EXISTS idx_checkins_event_created
    ON ticket_checkins(event_id, created_at DESC);

ALTER TABLE ticket_checkins
    ADD CONSTRAINT ticket_checkins_event_id_fkey
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE RESTRICT;

-- New application writes always include event_id. Keep historical NULLs possible for pre-V6 invalid scans.

ALTER TABLE ticket_checkins
    ADD CONSTRAINT chk_ticket_checkin_result
    CHECK (result IN ('ACCEPTED','ALREADY_USED','INVALID','CANCELLED','REFUNDED','WRONG_EVENT','STAFF_NOT_ASSIGNED','EVENT_CLOSED'));

ALTER TABLE events
    ADD CONSTRAINT chk_event_status
    CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','CANCELLED'));

ALTER TABLE ticket_types
    ADD CONSTRAINT chk_ticket_type_status
    CHECK (status IN ('ACTIVE','PAUSED','CLOSED'));

ALTER TABLE orders
    ADD CONSTRAINT chk_order_status
    CHECK (status IN ('CREATED','AWAITING_PAYMENT','CONFIRMED','EXPIRED','CANCELLED'));

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('CREATED','PENDING','PAYMENT_INITIATED','AUTHORIZED','CAPTURED','COMPLETED','FAILED','CANCELLED','REFUND_PENDING','REFUNDED'));
