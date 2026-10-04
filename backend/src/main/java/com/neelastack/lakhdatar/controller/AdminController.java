package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    private final EventManagementService eventService;
    private final RefundService refunds;
    private final OrganizerAdminService organizerAdmin;
    private final TeamService team;

    private UserPrincipal p(Authentication a) { return (UserPrincipal) a.getPrincipal(); }

    @GetMapping("/organizers")
    OrganizerAdminService.OrganizerList organizers(Authentication a) {
        UserPrincipal u = p(a); return organizerAdmin.list(u.userId(), u.role());
    }

    /** Organizer name and logo are provided here; the logo is uploaded to Cloudinary and only its URL is stored. */
    @PostMapping(value = "/organizers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<OrganizerAdminService.OrganizerSummary> createOrganizer(@RequestParam String name,
                                                                          @RequestParam String slug,
                                                                          @RequestParam(required = false) String description,
                                                                          @RequestParam(required = false) String website,
                                                                          @RequestPart(value = "logo", required = false) MultipartFile logo,
                                                                          Authentication a) {
        UserPrincipal u = p(a);
        return ResponseEntity.status(201).body(organizerAdmin.create(name, slug, description, website, logo, u.userId(), u.role()));
    }

    @GetMapping("/dashboard") AdminService.Dashboard dashboard(Authentication a) { return admin.dashboard(p(a)); }
    @GetMapping("/events") List<AdminService.EventSummary> events(Authentication a) { return admin.events(p(a)); }
    @GetMapping("/events/{id}") EventManagementService.AdminEventView event(@PathVariable UUID id, Authentication a) {
        UserPrincipal u = p(a);
        return eventService.getForAdmin(id, u.userId(), u.role());
    }

    @PostMapping("/events")
    ResponseEntity<EventManagementService.CreatedEvent> create(@Valid @RequestBody EventManagementService.CreateEventRequest b, Authentication a) {
        UserPrincipal u = p(a); return ResponseEntity.status(201).body(eventService.create(b, u.userId(), u.role()));
    }

    @PostMapping("/events/{id}/publish")
    ResponseEntity<Void> publish(@PathVariable UUID id, Authentication a) {
        UserPrincipal u = p(a); eventService.publish(id, u.userId(), u.role()); return ResponseEntity.noContent().build();
    }

    @PutMapping("/events/{id}")
    ResponseEntity<Void> update(@PathVariable UUID id, @Valid @RequestBody EventManagementService.UpdateEventRequest b, Authentication a) {
        UserPrincipal u = p(a); eventService.update(id, b, u.userId(), u.role()); return ResponseEntity.noContent().build();
    }
    @PostMapping("/events/{id}/unpublish") ResponseEntity<Void> unpublish(@PathVariable UUID id, Authentication a) { return transition(id, EventManagementService.Transition.UNPUBLISH, a); }
    @PostMapping("/events/{id}/cancel") ResponseEntity<Void> cancel(@PathVariable UUID id, Authentication a) { return transition(id, EventManagementService.Transition.CANCEL, a); }
    @PostMapping("/events/{id}/complete") ResponseEntity<Void> complete(@PathVariable UUID id, Authentication a) { return transition(id, EventManagementService.Transition.COMPLETE, a); }
    @PostMapping("/events/{id}/archive") ResponseEntity<Void> archive(@PathVariable UUID id, Authentication a) { return transition(id, EventManagementService.Transition.ARCHIVE, a); }
    private ResponseEntity<Void> transition(UUID id, EventManagementService.Transition t, Authentication a) {
        UserPrincipal u = p(a); eventService.transition(id, t, u.userId(), u.role()); return ResponseEntity.noContent().build();
    }
    @PostMapping("/events/{id}/ticket-types")
    ResponseEntity<EventManagementService.TicketTypeCreated> addTicketType(@PathVariable UUID id, @Valid @RequestBody EventManagementService.CreateTicketType b, Authentication a) {
        UserPrincipal u = p(a); return ResponseEntity.status(201).body(eventService.addTicketType(id, b, u.userId(), u.role()));
    }
    @PutMapping("/ticket-types/{id}")
    ResponseEntity<Void> updateTicketType(@PathVariable UUID id, @Valid @RequestBody EventManagementService.UpdateTicketType b, Authentication a) {
        UserPrincipal u = p(a); eventService.updateTicketType(id, b, u.userId(), u.role()); return ResponseEntity.noContent().build();
    }

    public record StaffAssignment(@NotBlank @Email @Size(max=255) String email, @NotBlank @Size(max=80) String gate) {}
    @PostMapping("/events/{id}/staff")
    ResponseEntity<Void> staff(@PathVariable UUID id, @Valid @RequestBody StaffAssignment b, Authentication a) {
        UserPrincipal u = p(a); admin.assignStaff(id, b.email(), b.gate(), u.userId(), u.role()); return ResponseEntity.noContent().build();
    }

    @PutMapping("/events/{id}/staff")
    ResponseEntity<Void> changeGate(@PathVariable UUID id, @Valid @RequestBody StaffAssignment b, Authentication a) {
        UserPrincipal u = p(a); team.changeStaffGate(id, b.email(), b.gate(), u.userId(), u.role()); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/events/{id}/staff")
    ResponseEntity<Void> removeStaff(@PathVariable UUID id, @RequestParam @Email @Size(max=255) String email, Authentication a) {
        UserPrincipal u = p(a); team.removeStaffAssignment(id, email, u.userId(), u.role()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/events/{id}/team")
    TeamService.EventTeam eventTeam(@PathVariable UUID id, Authentication a) { return team.eventTeam(id, p(a)); }

    /** @deprecated ADMIN-only; use POST /admin/organizers/{slug}/team/staff. */
    @Deprecated
    public record StaffCreate(@NotBlank @Email @Size(max=255) String email, @NotBlank @Size(max=120) String name, @NotBlank @Size(min = 12, max = 128) String password) {}

    public record ManagerCreate(@NotBlank @Email @Size(max=255) String email, @NotBlank @Size(min=2,max=120) String name, @NotBlank @Size(min=12,max=128) String password) {}
    /** @deprecated ADMIN-only; use POST /admin/organizers/{slug}/team/managers. */
    @Deprecated
    @PostMapping("/managers")
    ResponseEntity<Void> createManager(@Valid @RequestBody ManagerCreate b, Authentication a) {
        UserPrincipal u = p(a); admin.createManager(b.email(), b.name(), b.password(), u.userId(), u.role());
        return ResponseEntity.status(201).header("Deprecation", "true").header("Link", "</api/v1/admin/organizers/{slug}/team/managers>; rel=\"successor-version\"").build();
    }

    public record ManagerAssignment(@NotBlank @Email @Size(max=255) String email) {}
    @PostMapping("/events/{id}/managers")
    ResponseEntity<Void> assignManager(@PathVariable UUID id, @Valid @RequestBody ManagerAssignment b, Authentication a) {
        UserPrincipal u = p(a); admin.assignManager(id, b.email(), u.userId(), u.role()); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/events/{id}/managers")
    ResponseEntity<Void> unassignManager(@PathVariable UUID id, @RequestParam @Email @Size(max=255) String email, Authentication a) {
        UserPrincipal u = p(a); admin.unassignManager(id, email, u.userId(), u.role()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/events/{id}/ticket-types")
    List<AdminService.ManagerTicketType> ticketTypes(@PathVariable UUID id, Authentication a) { return admin.ticketTypesForEvent(id, p(a)); }
    @GetMapping("/events/{id}/managers")
    List<AdminService.ManagerView> managers(@PathVariable UUID id, Authentication a) { return admin.managersForEvent(id, p(a)); }

    /** @deprecated ADMIN-only; use POST /admin/organizers/{slug}/team/staff. */
    @Deprecated
    @PostMapping("/staff")
    ResponseEntity<Void> createStaff(@Valid @RequestBody StaffCreate b, Authentication a) {
        UserPrincipal u = p(a); admin.createStaff(b.email(), b.name(), b.password(), u.userId(), u.role());
        return ResponseEntity.status(201).header("Deprecation", "true").header("Link", "</api/v1/admin/organizers/{slug}/team/staff>; rel=\"successor-version\"").build();
    }

    @PostMapping("/payments/{paymentId}/refund")
    RefundService.RefundResult refund(@PathVariable UUID paymentId, @RequestParam(required = false, defaultValue = "Event cancellation") @Size(max=500) String reason, Authentication a) {
        UserPrincipal u = p(a); return refunds.refund(paymentId, reason, u.userId(), u.role());
    }

    @GetMapping(value = "/events/{eventId}/attendees.csv", produces = "text/csv")
    ResponseEntity<StreamingResponseBody> csv(@PathVariable UUID eventId, Authentication a) {
        UserPrincipal u = p(a);
        StreamingResponseBody body = output -> {
            try (var writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                admin.writeAttendeesCsv(eventId, u, writer);
            }
        };
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=attendees.csv").body(body);
    }
}
