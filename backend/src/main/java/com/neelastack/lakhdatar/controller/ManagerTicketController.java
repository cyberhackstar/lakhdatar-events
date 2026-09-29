package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.ManagerTicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/manager-tickets")
@RequiredArgsConstructor
public class ManagerTicketController {
    private final ManagerTicketService service;

    public record Body(
            @NotNull UUID eventId,
            @NotNull UUID ticketTypeId,
            @Min(1) @Max(100) int quantity,
            @NotBlank @Size(min=2,max=120) String attendeeName,
            @NotBlank @Email @Size(max=255) String attendeeEmail,
            @Size(max=40) String attendeePhone,
            @NotBlank @Size(min=16,max=100) String idempotencyKey) {}

    @PostMapping("/complimentary")
    ResponseEntity<ManagerTicketService.IssueResponse> issue(@Valid @RequestBody Body b, Authentication a) {
        UserPrincipal p = (UserPrincipal) a.getPrincipal();
        var result = service.issue(new ManagerTicketService.IssueRequest(
                b.eventId(), b.ticketTypeId(), b.quantity(), b.attendeeName(),
                b.attendeeEmail(), b.attendeePhone(), b.idempotencyKey()), p);
        return ResponseEntity.status(201).body(result);
    }
}
