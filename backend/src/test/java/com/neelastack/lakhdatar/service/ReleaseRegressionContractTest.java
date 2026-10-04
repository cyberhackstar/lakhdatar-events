package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseRegressionContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void publicSitemapShardsAreReachableAndPageBoundsAreEnforced() throws Exception {
        String security = read("src/main/java/com/neelastack/lakhdatar/config/SecurityConfig.java");
        String seo = read("src/main/java/com/neelastack/lakhdatar/controller/SeoController.java");
        assertTrue(security.contains("\"/sitemap-*.xml\""), "sitemap shards must be publicly reachable");
        assertTrue(seo.contains("long total = events.publishedEventCount();"), "sitemap shards must calculate the published-event count");
        assertTrue(seo.contains("if ((long) page > totalPages)"), "sitemap pages must reject out-of-range page numbers");
    }

    @Test
    void eventPublishAndUpdateUseTheSameParentRowLockAsInventory() throws Exception {
        String service = read("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java");
        int publishStart = service.indexOf("public void publish(");
        int transitionStart = service.indexOf("public void transition(");
        assertTrue(publishStart >= 0 && transitionStart > publishStart);
        String publish = service.substring(publishStart, transitionStart);
        assertTrue(publish.contains("events.findByPublicIdForUpdate(eventPublicId)"));

        int updateStart = service.indexOf("public void update(");
        int addTicketTypeStart = service.indexOf("public TicketTypeCreated addTicketType(");
        assertTrue(updateStart >= 0 && addTicketTypeStart > updateStart);
        String update = service.substring(updateStart, addTicketTypeStart);
        assertTrue(update.contains("events.findByIdForUpdate(managedEvent.getId())"));
    }
}
