# Production checklist

## Enterprise certification

- [ ] Exact release SHA passed `enterprise-release-qualification.yml`
- [ ] `ENTERPRISE_CERTIFIED_SHA` is set only after reviewing all CI/staging evidence
- [ ] DAST report reviewed and findings retested
- [ ] Independent penetration test completed for the production-equivalent release
- [ ] HA failover drill evidence attached
- [ ] PITR/restore drill evidence attached
- [ ] RPO/RTO and SLO targets approved
- [ ] Real Alertmanager paging destination configured and tested

## Application

- [ ] v2.0.13 enterprise release qualification checklist completed
- [ ] Phase 1 financial correctness, event cancellation/check-in race and refund recovery regression tests pass
- [ ] Operations Health console shows DB/Redis/recovery queues as expected
- [ ] Prometheus endpoint is reachable only through the private monitoring network

- [ ] CI passes backend `mvn clean verify`
- [ ] Cancellation refund retry test passes after a failed provider refund
- [ ] Cancellation/check-in race test passes
- [ ] Large-event cancellation test proves set-based execution remains bounded
- [ ] Frontend lockfile is regenerated and production build runs on the pinned Node/Angular baseline
- [ ] CI passes frontend `npm run build`
- [ ] Browser E2E enterprise qualification covers public security, ticket/PDF, admin/staff authorization, checkout idempotency, QR single-use, plus mobile browser coverage; privileged MFA/payment chaos remain mandatory environment tests
- [ ] ARM64 Docker images built successfully
- [ ] no secrets committed
- [ ] production `.env` created securely
- [ ] bootstrap disabled after setup
- [ ] `ALLOW_SINGLE_NODE_PRODUCTION` is explicitly acknowledged only when single-node downtime is an accepted business risk
- [ ] Privileged accounts have MFA enabled

## Razorpay

- [ ] live key ID/secret configured
- [ ] webhook endpoint registered
- [ ] live webhook secret configured
- [ ] payment signature verification tested
- [ ] provider capture state verified
- [ ] duplicate webhook test passed
- [ ] payment recovery/reconciliation test passed

## Scanner

- [ ] Android Chrome camera tested
- [ ] iPhone Safari/Chrome camera tested
- [ ] iPhone form fields tested: focus does not trigger persistent zoom
- [ ] camera permission denied path tested
- [ ] valid ticket tested
- [ ] duplicate ticket tested
- [ ] cancelled/refunded ticket tested
- [ ] wrong-event ticket tested
- [ ] internet-loss path tested
- [ ] concurrent same-ticket check-in tested

## Data protection

- [ ] PostgreSQL backup configured
- [ ] secondary backup configured off-host
- [ ] remote backup upload + `head-object` verification passes
- [ ] backup encryption policy explicitly uses AES256 or KMS
- [ ] restore drill evidence stored with RPO/RTO
- [ ] restore test completed
- [ ] application audit logs retained
- [ ] disk-space monitoring enabled

## High availability / disaster recovery

- [ ] Two application VMs deployed in separate failure domains
- [ ] External/managed PostgreSQL HA + continuous WAL/PITR enabled
- [ ] External Redis HA/TLS enabled
- [ ] At least two ingress/tunnel connectors configured
- [ ] Monthly restore drill completed and measured RPO/RTO recorded

## Load / resilience

- [ ] Catalog load test passed
- [ ] Dedicated staging checkout load test passed
- [ ] Check-in concurrency load test passed
- [ ] Payment-provider chaos scenarios passed without duplicate orders/tickets/refunds
- [ ] Enterprise staging gate ran with checkout/check-in credentials (not skipped)
- [ ] 1,000-user k6 profile completed with no integrity defects

## Deployment

- [ ] Cloudflare hostname routes to `127.0.0.1:4002`
- [ ] HTTPS verified
- [ ] public API is not directly exposed
- [ ] health checks pass
- [ ] rollback tested
- [ ] event-manager account can access/scan only explicitly assigned events
- [ ] manager-issued complimentary ticket shows provenance and consumes inventory
- [ ] previous image tag retained
