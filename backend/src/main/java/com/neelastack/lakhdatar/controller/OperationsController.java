package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.OperationsHealthService;
import com.neelastack.lakhdatar.service.OperationsDashboardService;
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
}
