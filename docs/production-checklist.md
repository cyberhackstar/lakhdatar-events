# Production checklist

## Application

- [ ] v1.9.31 enterprise release qualification checklist completed
- [ ] Operations Health console shows DB/Redis/recovery queues as expected
- [ ] Prometheus endpoint is reachable only through the private monitoring network

- [ ] CI passes backend `mvn verify`
- [ ] Frontend lockfile is regenerated and production build runs on the pinned Node/Angular baseline
- [ ] CI passes frontend `npm run build`
- [ ] ARM64 Docker images built successfully
- [ ] no secrets committed
- [ ] production `.env` created securely
- [ ] bootstrap disabled after setup

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

## Deployment

- [ ] Cloudflare hostname routes to `127.0.0.1:4002`
- [ ] HTTPS verified
- [ ] public API is not directly exposed
- [ ] health checks pass
- [ ] rollback tested
- [ ] event-manager account can access/scan only explicitly assigned events
- [ ] manager-issued complimentary ticket shows provenance and consumes inventory
- [ ] previous image tag retained
