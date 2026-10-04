package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.ManagerTicketService;
import com.neelastack.lakhdatar.service.TicketMailService;
import com.neelastack.lakhdatar.repository.OrderRepository;
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
    private final TicketMailService mail;
    private final OrderRepository orders;

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
        // The transaction has committed here, so the tickets exist. Email is sent after, and its outcome is reported honestly.
        String emailStatus = orders.findByPublicId(UUID.fromString(result.orderPublicId()))
                .map(o -> mail.sendNow(o.getId())).orElse(TicketMailService.FAILED);
        return ResponseEntity.status(201).body(result.withEmailStatus(emailStatus));
    }

    /** Re-sends the ticket email (QR + links) for an order the actor is allowed to manage. */
    @PostMapping("/orders/{orderId}/email")
    ResponseEntity<java.util.Map<String, String>> resend(@PathVariable UUID orderId, Authentication a) {
        return ResponseEntity.ok(java.util.Map.of("emailStatus", mail.resend(orderId, (UserPrincipal) a.getPrincipal())));
    }
}
