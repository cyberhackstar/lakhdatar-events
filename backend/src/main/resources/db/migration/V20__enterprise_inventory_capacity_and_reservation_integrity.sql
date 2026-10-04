-- Enterprise inventory correctness hardening.
-- Forward-only migration; prior migrations remain immutable.

-- Keep reservations referentially attached to orders whenever an order id is present.
-- The column remains nullable for compatibility with historical rows created before the
-- order-first checkout invariant was introduced.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_ticket_reservations_order'
          AND conrelid = 'ticket_reservations'::regclass
    ) THEN
        ALTER TABLE ticket_reservations
            ADD CONSTRAINT fk_ticket_reservations_order
            FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE NOT VALID;
    END IF;
END $$;

-- The application already serializes capacity mutations on the event row. This trigger is
-- the database-level backstop for direct SQL/import paths and for future code paths.
CREATE OR REPLACE FUNCTION enforce_event_ticket_capacity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    event_capacity INTEGER;
    configured_inventory BIGINT;
BEGIN
    SELECT capacity
      INTO event_capacity
      FROM events
     WHERE id = COALESCE(NEW.event_id, OLD.event_id)
     FOR UPDATE;

    IF event_capacity IS NULL THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    SELECT COALESCE(SUM(total_quantity), 0)
      INTO configured_inventory
      FROM ticket_types
     WHERE event_id = COALESCE(NEW.event_id, OLD.event_id);

    IF configured_inventory > event_capacity THEN
        RAISE EXCEPTION 'Ticket inventory % exceeds event capacity % for event %',
            configured_inventory, event_capacity, COALESCE(NEW.event_id, OLD.event_id)
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_ticket_types_event_capacity ON ticket_types;
CREATE CONSTRAINT TRIGGER trg_ticket_types_event_capacity
AFTER INSERT OR UPDATE OF event_id, total_quantity OR DELETE ON ticket_types
DEFERRABLE INITIALLY IMMEDIATE
FOR EACH ROW
EXECUTE FUNCTION enforce_event_ticket_capacity();

-- Also protect the invariant if a future admin/API path makes event capacity editable.
CREATE OR REPLACE FUNCTION enforce_event_capacity_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    configured_inventory BIGINT;
BEGIN
    IF NEW.capacity IS NULL OR NEW.capacity = OLD.capacity THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(SUM(total_quantity), 0)
      INTO configured_inventory
      FROM ticket_types
     WHERE event_id = NEW.id;

    IF configured_inventory > NEW.capacity THEN
        RAISE EXCEPTION 'Ticket inventory % exceeds new event capacity % for event %',
            configured_inventory, NEW.capacity, NEW.id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_events_capacity_change ON events;
CREATE TRIGGER trg_events_capacity_change
BEFORE UPDATE OF capacity ON events
FOR EACH ROW
EXECUTE FUNCTION enforce_event_capacity_change();

CREATE INDEX IF NOT EXISTS idx_ticket_reservations_order_status
    ON ticket_reservations(order_id, status, expires_at);
