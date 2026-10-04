package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Repository-level contracts that keep the multi-event / deployment requirements from regressing. */
class MultiEventContractTest {

    @Test void eventStatusConstraintMatchesDomainEnum() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V10__multi_event_catalog.sql"));
        int start = sql.indexOf("chk_event_status");
        assertTrue(start >= 0);
        int open = sql.indexOf("CHECK (status IN (", start);
        int end = sql.indexOf("));", open);
        var m = Pattern.compile("'([A-Z_]+)'").matcher(sql.substring(open, end));
        Set<String> actual = new HashSet<>();
        while (m.find()) actual.add(m.group(1));
        Set<String> expected = Arrays.stream(Enums.EventStatus.values()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    @Test void migrationIsAdditiveAndNeverTouchesFinancialTables() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V10__multi_event_catalog.sql")).toLowerCase();
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("delete from"));
        assertFalse(sql.contains("truncate"));
        for (String t : new String[]{"orders", "payments", "tickets", "refunds", "order_items", "ticket_reservations"})
            assertFalse(Pattern.compile("alter\\s+table\\s+" + t + "\\b").matcher(sql).find(), "V10 must not alter " + t);
    }

    @Test void publicApiNeverAcceptsClientSuppliedPricesOrOrganizerOnCheckout() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/OrderService.java"));
        assertTrue(src.contains("Ticket type does not belong to this event"), "cross-event ticket types must be rejected");
        assertTrue(src.contains("locked.getPriceMinorUnits()"), "price must come from the locked database row");
    }

    @Test void noHardCodedDefaultEventOrProductionLocalhost() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("../frontend/src"))) {
            for (Path p : files.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".ts")).toList()) {
                String s = Files.readString(p);
                assertFalse(s.contains("defaultEventSlug"), p + " must not reference a default event");
            }
        }
        assertFalse(Files.readString(Path.of("../frontend/src/environments/environment.prod.ts")).contains("localhost"));
    }

    @Test void deploymentUsesApprovedPathAndPortOnly() throws Exception {
        for (String f : new String[]{"../infra/docker-compose.prod.yml", "../infra/deploy/deploy.sh", "../infra/deploy/rollback.sh",
                "../.github/workflows/production.yml", "../.env.example", "../infra/backup/backup-postgres.sh"}) {
            String s = Files.readString(Path.of(f));
            assertFalse(s.contains("/opt/"), f + " must not use /opt");
            assertFalse(Pattern.compile("[:\\s\"'](4000|4001)\\b").matcher(s).find(), f + " must not claim ports 4000/4001");
        }
        String compose = Files.readString(Path.of("../infra/docker-compose.prod.yml"));
        assertTrue(compose.contains("127.0.0.1:4002:8080"), "public entry must be host port 4002, bound to loopback");
        assertTrue(Files.readString(Path.of("../.github/workflows/production.yml")).contains("/home/ubuntu/apps/lakhdatar-events"));
    }

    @Test void backendReadinessContractIsConsistentAcrossDeploymentLayers() throws Exception {
        String deploy = Files.readString(Path.of("../infra/deploy/deploy.sh"));
        String compose = Files.readString(Path.of("../infra/docker-compose.prod.yml"));
        String dockerfile = Files.readString(Path.of("../backend/Dockerfile"));
        String readiness = "/actuator/health/readiness";
        assertTrue(deploy.contains(readiness), "deploy gate must use the readiness group");
        assertTrue(compose.contains(readiness), "production Compose healthcheck must use the readiness group");
        assertTrue(dockerfile.contains(readiness), "backend image healthcheck must use the readiness group");
        assertTrue(deploy.contains("BACKEND_READINESS_TIMEOUT_SECONDS=\"${BACKEND_READINESS_TIMEOUT_SECONDS:-300}\""), "deploy gate must allow slow ARM/DB startup");
        assertTrue(compose.contains("start_period: 90s"), "Compose must allow application startup before readiness failures count");
        assertTrue(compose.contains("retries: 24"), "Compose readiness retries must provide a bounded recovery window");
        assertTrue(deploy.contains("print_backend_diagnostics"), "readiness failures must emit actionable backend diagnostics");
        assertFalse(deploy.contains("{1..40}"), "legacy 120-second backend gate must not return");
    }

    @Test void mobileFormControlsStayAtSixteenPixelsAndViewportRemainsAccessible() throws Exception {
        String html = Files.readString(Path.of("../frontend/src/index.html")).toLowerCase();
        assertTrue(html.contains("width=device-width"));
        assertFalse(html.contains("user-scalable=no"));
        assertFalse(html.contains("maximum-scale=1"));
        String css = Files.readString(Path.of("../frontend/src/styles.css")).replaceAll("\\s+", "");
        assertTrue(css.contains("input,select,textarea{font-size:16px!important;}"), "global editable controls must remain >=16px to prevent iOS focus zoom");
    }

    @Test void managerScopeIsEnforcedAtDashboardAndScannerBoundaries() throws Exception {
        String admin = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AdminService.java"));
        String staff = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/StaffController.java"));
        assertTrue(admin.contains("event_manager_assignments ema"));
        assertTrue(admin.contains("ema.event_id=e.id"));
        assertTrue(admin.contains("ema.user_id=?"));
        assertTrue(staff.contains("findAllByManagerUserIdOrderByStartsAtDesc"));
        String access = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/EventAccessService.java"));
        assertTrue(access.contains("EVENT_MANAGER") && access.contains("existsByEventIdAndUserId"));
    }

    @Test void sitemapAndRobotsExcludePrivateAreas() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/SeoController.java"));
        for (String p : new String[]{"/admin", "/staff", "/checkout", "/api/"}) assertTrue(src.contains("Disallow: " + p));
        assertFalse(src.contains("getHeader(\"Host\")"), "sitemap must not be built from the Host header");
    }
}
