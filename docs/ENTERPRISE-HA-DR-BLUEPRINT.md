# Enterprise HA / DR blueprint

This document defines the deployment target for high availability. It is not a claim that the single-VM Compose stack is highly available.

## Application tier

Run at least two stateless edge/web/API nodes on separate VMs or failure domains. Put Cloudflare Load Balancing/Tunnel connectors in front of the nodes and health-check both edge and application readiness. Keep `WORKER_ENABLED=false` on API nodes and run dedicated worker replicas with distributed Redis locks.

## PostgreSQL

Use a managed PostgreSQL primary/standby service or an equivalent two-node PostgreSQL HA design with synchronous/asynchronous replication according to the business RPO. Enable WAL archiving to off-host encrypted object storage and test point-in-time restore. The application must treat the DB endpoint as a connection string that can move during failover.

## Redis

Use a managed Redis HA deployment or Redis Sentinel/Cluster with automatic failover. Redis remains an acceleration/coordination layer; PostgreSQL remains the source of truth for orders, payments, tickets and financial ledger entries. Rate limiting should remain fail-closed when Redis is unavailable.

## Recovery objectives

Set explicit contractual targets before launch, for example RPO <= 5 minutes and RTO <= 30 minutes. Perform quarterly restore/failover drills and retain evidence.

## Release gates

A release is not considered HA-qualified until: (1) two application nodes remain healthy while one is stopped, (2) PostgreSQL failover is completed without corrupting financial/ticket state, (3) Redis failover does not authorize duplicate check-ins or payments, (4) provider recovery converges after network faults, and (5) PITR restore reproduces the expected ledger/order/ticket state.
