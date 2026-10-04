-- v1.9.29: append-only financial ledger for platform finance/reconciliation.
-- SALE entries are created when a payment first reaches CAPTURED/COMPLETED.
-- REFUND entries are created when a refund first reaches COMPLETED.

CREATE TABLE IF NOT EXISTS financial_ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    entry_type VARCHAR(16) NOT NULL,
    organizer_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    payment_id BIGINT,
    refund_id BIGINT,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    reference VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_financial_ledger_type CHECK (entry_type IN ('SALE','REFUND')),
    CONSTRAINT ck_financial_ledger_amount CHECK (amount_minor <> 0),
    CONSTRAINT uk_financial_ledger_reference UNIQUE (reference)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_financial_ledger_sale_payment
    ON financial_ledger_entries(payment_id) WHERE entry_type='SALE' AND payment_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_financial_ledger_refund_refund
    ON financial_ledger_entries(refund_id) WHERE entry_type='REFUND' AND refund_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_financial_ledger_organizer_created
    ON financial_ledger_entries(organizer_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_financial_ledger_event_created
    ON financial_ledger_entries(event_id, created_at DESC, id DESC);

CREATE OR REPLACE FUNCTION lakhdatar_financial_ledger_from_payment() RETURNS trigger AS $$
BEGIN
    IF NEW.status IN ('CAPTURED','COMPLETED') AND OLD.status NOT IN ('CAPTURED','COMPLETED') THEN
        INSERT INTO financial_ledger_entries(public_id, entry_type, organizer_id, event_id, order_id, payment_id, amount_minor, currency, reference)
        SELECT gen_random_uuid(), 'SALE', e.organizer_id, e.id, o.id, NEW.id, NEW.amount_minor, NEW.currency,
               'SALE-' || NEW.id::text
        FROM orders o JOIN events e ON e.id=o.event_id
        WHERE o.id=NEW.order_id
        ON CONFLICT (reference) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION lakhdatar_financial_ledger_from_refund() RETURNS trigger AS $$
BEGIN
    IF NEW.status='COMPLETED' AND OLD.status <> 'COMPLETED' THEN
        INSERT INTO financial_ledger_entries(public_id, entry_type, organizer_id, event_id, order_id, payment_id, refund_id, amount_minor, currency, reference)
        SELECT gen_random_uuid(), 'REFUND', e.organizer_id, e.id, o.id, p.id, NEW.id, -NEW.amount_minor, p.currency,
               'REFUND-' || NEW.id::text
        FROM payments p JOIN orders o ON o.id=p.order_id JOIN events e ON e.id=o.event_id
        WHERE p.id=NEW.payment_id
        ON CONFLICT (reference) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_financial_ledger_payment ON payments;
CREATE TRIGGER trg_financial_ledger_payment
AFTER UPDATE OF status ON payments
FOR EACH ROW EXECUTE FUNCTION lakhdatar_financial_ledger_from_payment();

DROP TRIGGER IF EXISTS trg_financial_ledger_refund ON refunds;
CREATE TRIGGER trg_financial_ledger_refund
AFTER UPDATE OF status ON refunds
FOR EACH ROW EXECUTE FUNCTION lakhdatar_financial_ledger_from_refund();

-- Backfill already-settled history without creating duplicates.
INSERT INTO financial_ledger_entries(public_id, entry_type, organizer_id, event_id, order_id, payment_id, amount_minor, currency, reference)
SELECT gen_random_uuid(), 'SALE', e.organizer_id, e.id, o.id, p.id, p.amount_minor, p.currency, 'SALE-' || p.id::text
FROM payments p JOIN orders o ON o.id=p.order_id JOIN events e ON e.id=o.event_id
WHERE p.status IN ('CAPTURED','COMPLETED')
  AND NOT EXISTS (SELECT 1 FROM financial_ledger_entries l WHERE l.payment_id=p.id AND l.entry_type='SALE');

INSERT INTO financial_ledger_entries(public_id, entry_type, organizer_id, event_id, order_id, payment_id, refund_id, amount_minor, currency, reference)
SELECT gen_random_uuid(), 'REFUND', e.organizer_id, e.id, o.id, p.id, r.id, -r.amount_minor, p.currency, 'REFUND-' || r.id::text
FROM refunds r JOIN payments p ON p.id=r.payment_id JOIN orders o ON o.id=p.order_id JOIN events e ON e.id=o.event_id
WHERE r.status='COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM financial_ledger_entries l WHERE l.refund_id=r.id AND l.entry_type='REFUND');

CREATE OR REPLACE FUNCTION lakhdatar_financial_ledger_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'financial ledger is append-only';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_financial_ledger_immutable ON financial_ledger_entries;
CREATE TRIGGER trg_financial_ledger_immutable
BEFORE UPDATE OR DELETE ON financial_ledger_entries
FOR EACH ROW EXECUTE FUNCTION lakhdatar_financial_ledger_immutable();
