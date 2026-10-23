-- Read-only financial and payment-state invariants. One row per check: name | severity | violations.
-- severity FAIL = money/fulfilment inconsistency; WARN = needs human review but may be transient.
-- Parameter: :stale_hours (psql -v stale_hours=24). Run inside a READ ONLY transaction.
SELECT 'paid_but_order_not_confirmed' AS check_name, 'FAIL' AS severity, count(*) AS violations
FROM payments p JOIN orders o ON o.id=p.order_id
WHERE p.status IN ('CAPTURED','COMPLETED') AND o.status IN ('CREATED','AWAITING_PAYMENT','EXPIRED')
UNION ALL
SELECT 'captured_without_ledger_sale','FAIL',count(*) FROM payments p
WHERE p.status IN ('CAPTURED','COMPLETED','REFUND_PENDING','REFUNDED')
  AND NOT EXISTS (SELECT 1 FROM financial_ledger_entries l WHERE l.payment_id=p.id AND l.entry_type='SALE')
UNION ALL
SELECT 'ledger_sale_amount_mismatch','FAIL',count(*) FROM financial_ledger_entries l JOIN payments p ON p.id=l.payment_id
WHERE l.entry_type='SALE' AND l.amount_minor<>p.amount_minor
UNION ALL
SELECT 'completed_refund_without_ledger_entry','FAIL',count(*) FROM refunds r
WHERE r.status='COMPLETED' AND NOT EXISTS (SELECT 1 FROM financial_ledger_entries l WHERE l.refund_id=r.id AND l.entry_type='REFUND')
UNION ALL
SELECT 'ledger_refund_amount_mismatch','FAIL',count(*) FROM financial_ledger_entries l JOIN refunds r ON r.id=l.refund_id
WHERE l.entry_type='REFUND' AND l.amount_minor<>-r.amount_minor
UNION ALL
SELECT 'refunds_exceed_payment','FAIL',count(*) FROM (
  SELECT p.id FROM payments p JOIN refunds r ON r.payment_id=p.id AND r.status IN ('REQUESTED','PROCESSING','COMPLETED')
  GROUP BY p.id, p.amount_minor HAVING sum(r.amount_minor) > p.amount_minor) x
UNION ALL
SELECT 'captured_amount_differs_from_order_total','FAIL',count(*) FROM payments p JOIN orders o ON o.id=p.order_id
WHERE p.status IN ('CAPTURED','COMPLETED') AND p.amount_minor<>o.total_minor_units
UNION ALL
SELECT 'confirmed_order_ticket_count_mismatch','FAIL',count(*) FROM orders o
WHERE o.status='CONFIRMED' AND o.total_minor_units>=0
  AND (SELECT coalesce(sum(quantity),0) FROM order_items i WHERE i.order_id=o.id)
   <> (SELECT count(*) FROM tickets t WHERE t.order_id=o.id)
  AND EXISTS (SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.status IN ('CAPTURED','COMPLETED'))
UNION ALL
SELECT 'inventory_oversold','FAIL',count(*) FROM ticket_types WHERE sold_quantity+reserved_quantity>total_quantity
UNION ALL
SELECT 'ticket_double_checkin','FAIL',count(*) FROM (
  SELECT ticket_id FROM ticket_checkins WHERE result='ACCEPTED' GROUP BY ticket_id HAVING count(*)>1) d
UNION ALL
SELECT 'stale_pending_payments','WARN',count(*) FROM payments
WHERE status IN ('CREATED','PENDING','PAYMENT_INITIATED') AND created_at < now() - make_interval(hours => :stale_hours)
UNION ALL
SELECT 'refunds_needing_manual_review','WARN',count(*) FROM refunds WHERE manual_review_required
UNION ALL
SELECT 'refunds_stuck','WARN',count(*) FROM refunds
WHERE status IN ('REQUESTED','PROCESSING','FAILED') AND created_at < now() - make_interval(hours => :stale_hours)
UNION ALL
SELECT 'dead_letter_webhooks','WARN',count(*) FROM payment_webhook_events WHERE dead_letter
UNION ALL
SELECT 'unprocessed_old_webhooks','WARN',count(*) FROM payment_webhook_events
WHERE NOT processed AND NOT dead_letter AND received_at < now() - make_interval(hours => :stale_hours);
