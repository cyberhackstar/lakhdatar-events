# Enterprise HA reference architecture

v1.9.46 is stateless at the HTTP/application tier and can run multiple backend and SSR instances. True high availability cannot be created by running more containers on the same Oracle VM; the VM remains a single failure domain.

## Recommended production topology

```text
                 Cloudflare
                      |
          Cloudflare Load Balancer / Tunnel pool
                 /             \
        Oracle VM A           Oracle VM B
        edge + web + API      edge + web + API
               \                 /
                \               /
              Managed PostgreSQL
              primary + standby + PITR
                       |
                 Managed Redis HA
```

Each application VM should use the same immutable image tag and external PostgreSQL/Redis endpoints. Application nodes must not store business state on local disk. Uploaded media belongs in Cloudinary/object storage. Ticket/email/payment state belongs in PostgreSQL.

## Docker Compose deployment note

Docker Compose does not apply Swarm's `deploy.replicas` semantics. The reference file therefore intentionally has no replica count. For a single VM capacity test, run `docker compose -f docker-compose.ha.example.yml up -d --scale backend=2 --scale web=2`. For real HA, run one stack on each of at least two VMs and place Cloudflare Load Balancing/Tunnel health checks in front of them; scaling multiple containers on one VM is not HA.

## Safe scaling rules

- Keep backend and SSR containers stateless.
- Do not use in-memory sessions, local ticket state, or local job queues as a source of truth.
- Scheduled jobs use distributed locks, so multiple backend replicas do not intentionally execute the same sweep simultaneously.
- Use a managed PostgreSQL service or a PostgreSQL primary/standby design with tested failover.
- Use Redis with replication/failover when Redis is part of the production rate-limit/cache path.
- Put at least two application VMs behind Cloudflare Load Balancing or an equivalent health-checked layer.
- Run at least two Cloudflare Tunnel connectors in separate failure domains when tunnels are the ingress path.

The existing single-VM Compose stack remains supported as a lower-complexity deployment. For same-VM load distribution, Docker Compose can run multiple stateless replicas with `docker compose -f docker-compose.ha.example.yml up -d --scale backend=2 --scale web=2`; this improves process-level capacity but is still one failure domain. True HA requires the stack on at least two VMs plus independent ingress/load balancing and durable external data services. This HA reference profile is the architecture target for higher availability, not a claim that one VM is highly available.
