package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.OperationsHealthService;
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

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    OperationsHealthService.Health health() { return ops.health(); }
}
