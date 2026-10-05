package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Guards the architectural boundary between organizer business operations and the SRE monitor surface. */
class MonitorSeparationContractTest {
    @Test
    void frontendHasDedicatedMonitorRouteAndAdminGuard() throws Exception {
        String routes = Files.readString(Path.of("../frontend/src/app/app.routes.ts"));
        String guard = Files.readString(Path.of("../frontend/src/app/core/auth/auth.guard.ts"));
        String monitor = Files.readString(Path.of("../frontend/src/app/features/monitor-console.component.ts"));
        String business = Files.readString(Path.of("../frontend/src/app/features/admin/operations-center.component.ts"));
        assertTrue(routes.contains("path: 'monitor'"));
        assertTrue(routes.contains("platformAdminGuard"));
        assertTrue(guard.contains("auth.role() === 'ADMIN'"));
        assertTrue(monitor.contains("Dedicated SRE console"));
        assertTrue(monitor.contains("https://events.neelastack.com/admin/operations"));
        assertTrue(monitor.contains("Live logs"));
        assertTrue(monitor.contains("Incidents &amp; warnings"));
        assertFalse(business.contains("Live logs"));
    }

    @Test
    void edgeSeparatesMonitorHostname() throws Exception {
        String edge = Files.readString(Path.of("../edge/nginx.conf"));
        String ha = Files.readString(Path.of("../infra/ha/nginx-ha.conf.example"));
        for (String source : new String[]{edge, ha}) {
            assertTrue(source.contains("map $host $is_monitor_host"));
            assertTrue(source.contains("location = /monitor"));
            assertTrue(source.contains("if ($is_monitor_host = 0) { return 404; }"));
            assertTrue(source.contains("location ^~ /admin"));
            assertTrue(source.contains("if ($is_monitor_host = 1) { return 404; }"));
            assertTrue(source.contains("return 302 /monitor$is_args$args;"));
        }
    }
}
