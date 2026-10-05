# Enterprise capability matrix

| Capability | Application implementation | Environment evidence required |
|---|---|---|
| Secure authentication | ✅ | E2E + penetration retest |
| Privileged MFA | ✅ | production enabled + recovery drill |
| Password recovery | ✅ | E2E/recovery tests |
| Inventory concurrency | ✅ | concurrency/load evidence |
| Payment idempotency | ✅ | sandbox chaos/load evidence |
| Refund recovery | ✅ | failed/refund retry evidence |
| Webhook durability | ✅ | duplicate/out-of-order/provider outage evidence |
| QR single-use | ✅ | mobile/concurrency evidence |
| Observability | ✅ | alerts/paging test |
| Backup/restore | ✅ tooling | executed DR evidence |
| HA | ✅ reference | two-failure-domain failover evidence |
| DAST | ✅ runner | staging scan + remediation |
| Browser E2E | ✅ desktop/mobile suite | staging browser evidence |
| Fresh/upgrade database qualification | ✅ Flyway V1–V37 gate | CI database evidence artifact |
| Privacy governance | policy baseline | business/legal approval |
