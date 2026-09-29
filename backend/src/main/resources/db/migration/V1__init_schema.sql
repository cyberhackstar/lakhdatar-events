-- Core schema. Public-facing identifiers use random UUIDs / tokens, never raw sequential PKs.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(32),
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(32) NOT NULL, -- ADMIN, ORGANIZER, EVENT_MANAGER, STAFF, FINANCE, SUPPORT, CUSTOMER
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organizers (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    website         VARCHAR(500),
    contact_email   VARCHAR(255),
    contact_phone   VARCHAR(32),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organizer_members (
    id              BIGSERIAL PRIMARY KEY,
    organizer_id    BIGINT NOT NULL REFERENCES organizers(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    role            VARCHAR(32) NOT NULL, -- OWNER, EVENT_MANAGER, STAFF, FINANCE, SUPPORT
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organizer_id, user_id)
);

CREATE TABLE brand_configurations (
    id                          BIGSERIAL PRIMARY KEY,
    public_id                   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    scope                       VARCHAR(32) NOT NULL, -- GLOBAL, ORGANIZER, EVENT
    organizer_id                BIGINT REFERENCES organizers(id),
    organizer_logo_url          VARCHAR(500),
    organizer_name              VARCHAR(255),
    event_logo_url              VARCHAR(500),
    event_banner_url            VARCHAR(500),
    primary_brand_color         VARCHAR(16),
    secondary_brand_color       VARCHAR(16),
    technology_partner_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    technology_partner_name     VARCHAR(255) DEFAULT 'Neelastack',
    technology_partner_logo_url VARCHAR(500),
    technology_partner_url      VARCHAR(500),
    promo_title                 VARCHAR(255),
    promo_description           TEXT,
    promo_cta_text              VARCHAR(255),
    promo_cta_url               VARCHAR(500),
    promo_enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE venues (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organizer_id    BIGINT NOT NULL REFERENCES organizers(id),
    name            VARCHAR(255) NOT NULL,
    address         VARCHAR(500),
    city            VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    organizer_id    BIGINT NOT NULL REFERENCES organizers(id),
    venue_id        BIGINT REFERENCES venues(id),
    brand_config_id BIGINT REFERENCES brand_configurations(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ,
    capacity        INTEGER,
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, UNPUBLISHED, CANCELLED
    currency        VARCHAR(8) NOT NULL DEFAULT 'INR',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event_staff (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    gate        VARCHAR(120),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)
);

CREATE TABLE ticket_types (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    event_id            BIGINT NOT NULL REFERENCES events(id),
    name                VARCHAR(120) NOT NULL,
    description         TEXT,
    price_minor_units   BIGINT NOT NULL, -- integer minor currency units; never float
    currency            VARCHAR(8) NOT NULL DEFAULT 'INR',
    total_quantity      INTEGER NOT NULL,
    reserved_quantity   INTEGER NOT NULL DEFAULT 0,
    sold_quantity       INTEGER NOT NULL DEFAULT 0,
    min_per_order       INTEGER NOT NULL DEFAULT 1,
    max_per_order       INTEGER NOT NULL DEFAULT 10,
    sale_starts_at      TIMESTAMPTZ,
    sale_ends_at        TIMESTAMPTZ,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, CLOSED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_inventory_non_negative CHECK (
        reserved_quantity >= 0 AND sold_quantity >= 0
        AND (reserved_quantity + sold_quantity) <= total_quantity
    )
);

CREATE TABLE ticket_reservations (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    ticket_type_id  BIGINT NOT NULL REFERENCES ticket_types(id),
    order_id        BIGINT, -- set once order created
    quantity        INTEGER NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'HELD', -- HELD, CONFIRMED, RELEASED, EXPIRED
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    order_number         VARCHAR(40) NOT NULL UNIQUE,
    event_id            BIGINT NOT NULL REFERENCES events(id),
    user_id             BIGINT REFERENCES users(id),
    customer_name       VARCHAR(255) NOT NULL,
    customer_email      VARCHAR(255) NOT NULL,
    customer_phone      VARCHAR(32),
    total_minor_units   BIGINT NOT NULL,
    currency            VARCHAR(8) NOT NULL DEFAULT 'INR',
    status              VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_items (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id),
    ticket_type_id       BIGINT NOT NULL REFERENCES ticket_types(id),
    quantity            INTEGER NOT NULL,
    unit_price_minor    BIGINT NOT NULL,
    subtotal_minor      BIGINT NOT NULL
);

CREATE TABLE payments (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    order_id                BIGINT NOT NULL REFERENCES orders(id),
    razorpay_order_id       VARCHAR(120),
    razorpay_payment_id     VARCHAR(120),
    razorpay_signature      VARCHAR(500),
    amount_minor            BIGINT NOT NULL,
    currency                VARCHAR(8) NOT NULL DEFAULT 'INR',
    status                  VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    -- CREATED, PENDING, PAYMENT_INITIATED, AUTHORIZED, CAPTURED, COMPLETED,
    -- FAILED, CANCELLED, REFUND_PENDING, REFUNDED
    failure_reason          VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    provider_event_id VARCHAR(150) NOT NULL UNIQUE, -- idempotency guard
    event_type      VARCHAR(80) NOT NULL,
    payload         JSONB NOT NULL,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ
);

CREATE TABLE refunds (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    payment_id      BIGINT NOT NULL REFERENCES payments(id),
    amount_minor    BIGINT NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(32) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, PROCESSING, COMPLETED, FAILED
    razorpay_refund_id VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    ticket_number        VARCHAR(40) NOT NULL UNIQUE, -- human readable, e.g. LK-XXXXXX
    order_id            BIGINT NOT NULL REFERENCES orders(id),
    order_item_id        BIGINT NOT NULL REFERENCES order_items(id),
    event_id            BIGINT NOT NULL REFERENCES events(id),
    ticket_type_id       BIGINT NOT NULL REFERENCES ticket_types(id),
    attendee_name       VARCHAR(255),
    qr_credential_hash   VARCHAR(128) NOT NULL UNIQUE, -- SHA-256 hex of the random token; raw token never stored
    status              VARCHAR(32) NOT NULL DEFAULT 'ISSUED', -- ISSUED, CANCELLED, REFUNDED, CHECKED_IN
    checked_in_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ticket_checkins (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT REFERENCES tickets(id), -- nullable: an INVALID scan may match no ticket at all
    staff_user_id   BIGINT REFERENCES users(id),
    gate            VARCHAR(120),
    result          VARCHAR(32) NOT NULL, -- ACCEPTED, ALREADY_USED, INVALID, CANCELLED, REFUNDED, WRONG_EVENT
    correlation_id  VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT REFERENCES orders(id),
    channel         VARCHAR(16) NOT NULL, -- EMAIL, SMS, WHATSAPP
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    payload_summary VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_user_id   BIGINT REFERENCES users(id),
    action          VARCHAR(80) NOT NULL,
    entity_type     VARCHAR(80),
    entity_id       VARCHAR(80),
    correlation_id  VARCHAR(80),
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_organizer ON events(organizer_id);
CREATE INDEX idx_ticket_types_event ON ticket_types(event_id);
CREATE INDEX idx_orders_event ON orders(event_id);
CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_tickets_event ON tickets(event_id);
CREATE INDEX idx_tickets_order ON tickets(order_id);
CREATE INDEX idx_checkins_ticket ON ticket_checkins(ticket_id);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
