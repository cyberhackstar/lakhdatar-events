-- Production hardening: stronger constraints, optimistic versions, indexes and refresh-token storage.

ALTER TABLE ticket_types ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payments ADD CONSTRAINT uq_payments_order UNIQUE (order_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_razorpay_order
    ON payments (razorpay_order_id) WHERE razorpay_order_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_razorpay_payment
    ON payments (razorpay_payment_id) WHERE razorpay_payment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ticket_reservations_expiry
    ON ticket_reservations (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_ticket_reservations_order
    ON ticket_reservations (order_id);
CREATE INDEX IF NOT EXISTS idx_event_staff_user_event
    ON event_staff (user_id, event_id);
CREATE INDEX IF NOT EXISTS idx_tickets_public_id
    ON tickets (public_id);
CREATE INDEX IF NOT EXISTS idx_orders_public_id
    ON orders (public_id);
CREATE INDEX IF NOT EXISTS idx_orders_email_created
    ON orders (customer_email, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_status_created
    ON payments (status, created_at);
CREATE INDEX IF NOT EXISTS idx_webhook_processed_received
    ON payment_webhook_events (processed, received_at);
CREATE INDEX IF NOT EXISTS idx_audit_created_at
    ON audit_logs (created_at DESC);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                      BIGSERIAL PRIMARY KEY,
    token_hash              VARCHAR(64) NOT NULL UNIQUE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at              TIMESTAMPTZ NOT NULL,
    revoked_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    replaced_by_token_hash  VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user
    ON refresh_tokens (user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires
    ON refresh_tokens (expires_at);

ALTER TABLE ticket_checkins
    DROP CONSTRAINT IF EXISTS ticket_checkins_ticket_id_fkey;
ALTER TABLE ticket_checkins
    ADD CONSTRAINT ticket_checkins_ticket_id_fkey
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE SET NULL;

ALTER TABLE payment_webhook_events
    ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_webhook_payload_hash
    ON payment_webhook_events (payload_hash) WHERE payload_hash IS NOT NULL;

-- Additional defense-in-depth invariants.
CREATE UNIQUE INDEX IF NOT EXISTS uq_organizer_members_pair
    ON organizer_members (organizer_id, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refunds_payment
    ON refunds (payment_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_accepted_checkin_ticket
    ON ticket_checkins (ticket_id) WHERE result = 'ACCEPTED' AND ticket_id IS NOT NULL;
ALTER TABLE order_items ADD CONSTRAINT chk_order_item_quantity_positive CHECK (quantity > 0);
ALTER TABLE ticket_reservations ADD CONSTRAINT chk_reservation_quantity_positive CHECK (quantity > 0);
ALTER TABLE payments ADD CONSTRAINT chk_payment_amount_non_negative CHECK (amount_minor >= 0);
ALTER TABLE refunds ADD CONSTRAINT chk_refund_amount_positive CHECK (amount_minor > 0);
