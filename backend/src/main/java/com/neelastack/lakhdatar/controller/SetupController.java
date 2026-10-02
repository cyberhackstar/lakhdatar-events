package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.ClientAddressService;
import com.neelastack.lakhdatar.service.InitialAdminSetupService;
import com.neelastack.lakhdatar.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {
    private final InitialAdminSetupService setup;
    private final RateLimitService rateLimits;
    private final ClientAddressService clientAddress;

    public record Body(@NotBlank @Email @Size(max = 255) String email,
                       @NotBlank @Size(min = 12, max = 128) String password,
                       @NotBlank @Size(min = 2, max = 120) String name,
                       @NotBlank @Size(min = 2, max = 255) String organizerName,
                       @NotBlank @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+){0,80}") String organizerSlug) {}

    @GetMapping("/initial-admin/status")
    ResponseEntity<InitialAdminSetupService.SetupStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(setup.status());
    }

    @PostMapping("/initial-admin")
    ResponseEntity<InitialAdminSetupService.SetupResult> create(@RequestHeader(value = "X-Initial-Setup-Token", required = false) String token,
                                                                  @Valid @RequestBody Body body,
                                                                  HttpServletRequest request) {
        if (!rateLimits.allow("initial-setup:" + clientAddress.resolve(request), 5, Duration.ofMinutes(10)))
            return ResponseEntity.status(429).cacheControl(CacheControl.noStore()).build();
        var result = setup.create(token, new InitialAdminSetupService.SetupRequest(body.email(), body.password(), body.name(), body.organizerName(), body.organizerSlug()));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(result);
    }
}
