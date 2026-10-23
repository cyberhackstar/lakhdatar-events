-- Control totals for the period [:from, :to) per provider and currency (minor units). Read-only.
SELECT p.provider, p.currency,
       count(*) FILTER (WHERE l.entry_type='SALE')                         AS sale_count,
       coalesce(sum(l.amount_minor) FILTER (WHERE l.entry_type='SALE'),0)   AS sales_minor,
       count(*) FILTER (WHERE l.entry_type='REFUND')                       AS refund_count,
       coalesce(sum(-l.amount_minor) FILTER (WHERE l.entry_type='REFUND'),0) AS refunds_minor,
       coalesce(sum(l.amount_minor),0)                                     AS net_minor
FROM financial_ledger_entries l JOIN payments p ON p.id = coalesce(l.payment_id, (SELECT payment_id FROM refunds WHERE id=l.refund_id))
WHERE l.created_at >= :'from'::timestamptz AND l.created_at < :'to'::timestamptz
GROUP BY p.provider, p.currency ORDER BY 1,2;
