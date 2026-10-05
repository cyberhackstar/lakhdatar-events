# HA failover certification

A deployment is not HA-certified until an operator proves the failure path. Two containers on one VM are not independent failure domains.

## Drill

1. Confirm two independent application/SSR nodes and HA PostgreSQL/Redis.
2. Record baseline API, checkout, ticket retrieval, and check-in latency.
3. Drain/terminate node A.
4. Verify the load balancer removes node A and traffic reaches node B.
5. Repeat an idempotent checkout request and confirm no duplicate order.
6. Retrieve a previously issued ticket.
7. Perform a dedicated QR check-in.
8. Recover node A and confirm it rejoins healthy.
9. Verify database replication/health and business totals.

Record: date, release SHA, node identifiers, observed RTO, observed data loss/RPO, failed requests, customer-visible impact, and corrective actions.

## Pass criteria

- No data divergence.
- No duplicate order/payment from a retried request.
- Existing ticket access remains valid.
- QR idempotency remains intact.
- Observed RTO/RPO meet the currently approved SLOs.
