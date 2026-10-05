package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.ClientAddressService;
import com.neelastack.lakhdatar.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService service;
    private final ClientAddressService clientAddress;

    public record RequestBody(@NotBlank @Email @Size(max=255) String email) {}
    public record ResetBody(@NotBlank @Size(max=256) String token, @NotBlank @Size(min=12,max=128) String newPassword) {}
    public record Accepted(boolean accepted) {}

    @PostMapping("/request")
    ResponseEntity<Accepted> request(@Valid @RequestBody RequestBody body, HttpServletRequest req) {
        var result = service.request(body.email(), clientAddress.resolve(req));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Accepted(result.accepted()));
    }

    @PostMapping("/complete")
    ResponseEntity<Void> complete(@Valid @RequestBody ResetBody body, HttpServletRequest req) {
        service.reset(body.token(), body.newPassword(), clientAddress.resolve(req));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
