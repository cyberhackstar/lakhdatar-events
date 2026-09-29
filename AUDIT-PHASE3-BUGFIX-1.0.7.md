# Phase 3 Bugfix 1.0.7

Fixed five compiler errors reported by the Windows Maven build:

1. `RefundService`: queued refund response now converts the refund public UUID to the `String` field expected by `RefundResult`.
2. `DistributedLockService`: corrected stale four-argument `Handle` constructor calls to the three-argument constructor.
3. `WebhookService`: added the Java 21 `java.util.HexFormat` import used for SHA-256 event fallback IDs.
4. `RefreshTokenRepository`: added the `deleteExpiredOrOldRevoked(Instant cutoff)` query required by `RefreshTokenCleanupJob`.
5. Release metadata aligned to 1.0.7.

Validation performed in this environment: JSON/YAML/XML parse checks, shell syntax checks, Java source structural/reference checks, source hygiene scan, and ZIP integrity. Full Maven dependency-resolved compilation remains to be run on the developer/CI environment.
