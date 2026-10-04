-- Durable ticket email delivery queue.
-- Email is non-critical to the sale, but delivery work must survive application restarts.

CREATE TABLE IF NOT EXISTS ticket_mail_jobs (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status            VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error        VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at           TIMESTAMPTZ,
    CONSTRAINT uq_ticket_mail_job_order UNIQUE (order_id),
    CONSTRAINT chk_ticket_mail_job_status CHECK (status IN ('PENDING','PROCESSING','SENT','SKIPPED','FAILED')),
    CONSTRAINT chk_ticket_mail_job_attempts_non_negative CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ticket_mail_jobs_due
    ON ticket_mail_jobs(status, next_attempt_at, created_at);
CREATE INDEX IF NOT EXISTS idx_ticket_mail_jobs_order
    ON ticket_mail_jobs(order_id);
