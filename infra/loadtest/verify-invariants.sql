\set ON_ERROR_STOP on

-- The target UUID is provided with psql -v and copied into a session-local setting before the
-- DO block, because psql variable interpolation is intentionally not relied on inside dollar quotes.
SELECT set_config('app.loadtest_event_public_id', :'event_public_id', false);

DO $loadtest$
DECLARE
  target_event_public_id uuid := current_setting('app.loadtest_event_public_id')::uuid;
  event_internal_id bigint;
  bad bigint;
BEGIN
  SELECT id INTO event_internal_id FROM events WHERE public_id = target_event_public_id;
  IF event_internal_id IS NULL THEN RAISE EXCEPTION 'LOADTEST_EVENT_NOT_FOUND'; END IF;

  SELECT count(*) INTO bad
  FROM ticket_types
  WHERE event_id = event_internal_id
    AND (total_quantity < 0 OR reserved_quantity < 0 OR sold_quantity < 0
         OR reserved_quantity + sold_quantity > total_quantity);
  IF bad > 0 THEN RAISE EXCEPTION 'INVENTORY_INVARIANT_BROKEN rows=%', bad; END IF;

  SELECT count(*) INTO bad
  FROM tickets t
  LEFT JOIN orders o ON o.id = t.order_id
  LEFT JOIN ticket_types tt ON tt.id = t.ticket_type_id
  WHERE t.event_id = event_internal_id
    AND (o.id IS NULL OR tt.id IS NULL OR tt.event_id <> event_internal_id);
  IF bad > 0 THEN RAISE EXCEPTION 'TICKET_ORPHAN_OR_EVENT_MISMATCH rows=%', bad; END IF;

  SELECT count(*) INTO bad
  FROM (
    SELECT p.provider_order_id
    FROM payments p
    JOIN orders o ON o.id = p.order_id
    WHERE o.event_id = event_internal_id AND p.provider_order_id IS NOT NULL
    GROUP BY p.provider_order_id
    HAVING count(*) > 1
  ) duplicate_provider_orders;
  IF bad > 0 THEN RAISE EXCEPTION 'DUPLICATE_PROVIDER_ORDER_REFERENCE count=%', bad; END IF;

  SELECT count(*) INTO bad
  FROM (
    SELECT oi.order_id, oi.id AS order_item_id, oi.quantity,
           count(t.id) AS issued
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    LEFT JOIN tickets t ON t.order_item_id = oi.id
    WHERE o.event_id = event_internal_id AND o.status = 'CONFIRMED'
    GROUP BY oi.order_id, oi.id, oi.quantity
    HAVING count(t.id) <> oi.quantity
  ) mismatches;
  IF bad > 0 THEN RAISE EXCEPTION 'CONFIRMED_ORDER_TICKET_COUNT_MISMATCH rows=%', bad; END IF;

  SELECT count(*) INTO bad
  FROM ticket_reservations r
  JOIN ticket_types tt ON tt.id = r.ticket_type_id
  WHERE tt.event_id = event_internal_id AND r.status = 'HELD' AND r.expires_at <= now();
  IF bad > 0 THEN RAISE EXCEPTION 'EXPIRED_HELD_RESERVATIONS_REMAIN rows=%', bad; END IF;

  SELECT count(*) INTO bad
  FROM payments p
  JOIN orders o ON o.id = p.order_id
  WHERE o.event_id = event_internal_id AND p.status = 'CAPTURED'
    AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.order_id = o.id);
  IF bad > 0 THEN RAISE EXCEPTION 'CAPTURED_PAYMENT_WITHOUT_TICKETS rows=%', bad; END IF;

  RAISE NOTICE 'LOADTEST_INVARIANTS_OK event=%', target_event_public_id;
END
$loadtest$;
