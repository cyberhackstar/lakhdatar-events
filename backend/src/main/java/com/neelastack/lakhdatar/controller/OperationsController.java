package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.OperationsHealthService;
import com.neelastack.lakhdatar.service.OperationsDashboardService;
import com.neelastack.lakhdatar.service.OperationsLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ops")
@RequiredArgsConstructor
public class OperationsController {
    private final OperationsHealthService ops;
    private final OperationsDashboardService dashboard;
    private final OperationsLogService logs;

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    OperationsHealthService.Health health() { return ops.health(); }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    org.springframework.http.ResponseEntity<OperationsDashboardService.Dashboard> dashboard() {
        return org.springframework.http.ResponseEntity.<OperationsDashboardService.Dashboard>ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(dashboard.snapshot());
    }
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    org.springframework.http.ResponseEntity<OperationsLogService.LogPage> logs(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String service,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String level,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer limit,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer sinceMinutes) {
        return org.springframework.http.ResponseEntity.<OperationsLogService.LogPage>ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(logs.query(service, level, q, limit, sinceMinutes));
    }

}
