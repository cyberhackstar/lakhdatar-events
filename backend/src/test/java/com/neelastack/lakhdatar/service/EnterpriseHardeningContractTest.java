package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EnterpriseHardeningContractTest {
    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    @Test
    void capacityMutationsLockTheParentEventAndDbHasAnInvariantBackstop() throws Exception {
        String service = read("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java");
        assertTrue(service.contains("events.findByIdForUpdate(managedEvent.getId())"));
        assertTrue(service.contains("events.findByIdForUpdate(found.getEventId())"));
        String migration = read("src/main/resources/db/migration/V20__enterprise_inventory_capacity_and_reservation_integrity.sql");
        assertTrue(migration.contains("FOR UPDATE"));
        assertTrue(migration.contains("trg_ticket_types_event_capacity"));
        assertTrue(migration.contains("trg_events_capacity_change"));
    }

    @Test
    void mailDeliveryIsDurableAndBounded() throws Exception {
        String service = read("src/main/java/com/neelastack/lakhdatar/service/TicketMailService.java");
        assertTrue(service.contains("ArrayBlockingQueue"));
        assertTrue(service.contains("mailJobs.findByOrderId"));
        assertTrue(service.contains("findConfirmedPaidWithoutMailJob"));
        assertTrue(service.contains("@Scheduled(fixedDelayString = \"${app.mail-delivery-sweep:30000}\")"));
        assertTrue(service.contains("cleanupMailHistory"));
        String migration = read("src/main/resources/db/migration/V21__durable_ticket_mail_jobs.sql");
        assertTrue(migration.contains("uq_ticket_mail_job_order"));
        assertTrue(migration.contains("CHECK (attempts >= 0)"));
        assertTrue(read("src/main/resources/db/migration/V23__enterprise_recovery_query_indexes.sql").contains("idx_orders_status_created_at"));
    }

    @Test
    void largeExportsAndSitemapsAreBounded() throws Exception {
        String admin = read("src/main/java/com/neelastack/lakhdatar/service/AdminService.java");
        assertTrue(admin.contains("setFetchSize(1000)"));
        String controller = read("src/main/java/com/neelastack/lakhdatar/controller/AdminController.java");
        assertTrue(controller.contains("ResponseEntity<byte[]>"));
        assertTrue(controller.contains("Content-Disposition"));
        assertTrue(controller.contains("contentLength(body.length)"));
        String seo = read("src/main/java/com/neelastack/lakhdatar/controller/SeoController.java");
        assertTrue(seo.contains("SITEMAP_PAGE_SIZE = 10_000"));
        assertTrue(seo.contains("/sitemap-{page:[0-9]+}.xml"));
        String nginx = read("../edge/nginx.conf");
        assertTrue(nginx.contains("sitemap-[0-9]+\\.xml"));
    }

    @Test
    void productionRuntimeKnobsAreExplicitlyExposed() throws Exception {
        String compose = read("../infra/docker-compose.prod.yml");
        for (String key : new String[]{"DB_POOL_MAX", "RESERVATION_HOLD", "RATE_LIMIT_FAIL_CLOSED", "MAX_TICKETS_PER_ORDER", "MAIL_DELIVERY_SWEEP"})
            assertTrue(compose.contains(key + ":"), "Missing production knob: " + key);
        assertTrue(read("../.env.example").contains("MAIL_DELIVERY_CLEANUP_SWEEP="));
    }

    @Test
    void cloudinaryUploadsDoNotHoldDatabaseTransactions() throws Exception {
        String assets = read("src/main/java/com/neelastack/lakhdatar/service/CloudinaryAssetService.java");
        assertFalse(assets.contains("@Transactional\n    public UploadResult upload"));
        assertTrue(assets.contains("@Transactional(propagation = Propagation.NOT_SUPPORTED)"));
        assertTrue(assets.contains("tx.executeWithoutResult"));
        assertTrue(assets.contains("compareAndSet(false, true)"));
    }
}
