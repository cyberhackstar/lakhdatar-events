package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Organizer-scoped team management. Authorization (ADMIN or the organizer's own OWNER) is enforced in TeamService. */
@RestController
@RequestMapping("/api/v1/admin/organizers/{slug}/team")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService team;

    /** password is optional: omit it to send an email invite; supply it (12+ chars) for the initial-password flow. */
    public record CreateBody(@NotBlank @Size(min = 2, max = 120) String name,
                             @NotBlank @Email @Size(max = 255) String email,
                             @Size(max = 32) String phone,
                             @Size(min = 12, max = 128) String password) {}
    public record PatchBody(@Size(min = 2, max = 120) String name, Boolean active) {}

    private UserPrincipal p(Authentication a) { return (UserPrincipal) a.getPrincipal(); }

    @GetMapping
    TeamService.TeamView list(@PathVariable @Size(max = 255) String slug, Authentication a) {
        UserPrincipal u = p(a); return team.list(slug, u.userId(), u.role());
    }

    @PostMapping("/staff")
    ResponseEntity<TeamService.CreatedMember> createStaff(@PathVariable @Size(max = 255) String slug, @Valid @RequestBody CreateBody b, Authentication a) {
        return create(slug, TeamService.Kind.STAFF, b, a);
    }

    @PostMapping("/managers")
    ResponseEntity<TeamService.CreatedMember> createManager(@PathVariable @Size(max = 255) String slug, @Valid @RequestBody CreateBody b, Authentication a) {
        return create(slug, TeamService.Kind.MANAGER, b, a);
    }

    /** Platform ADMIN only: gives an organizer its first (or an additional) owner login. */
    @PostMapping("/owners")
    ResponseEntity<TeamService.CreatedMember> createOwner(@PathVariable @Size(max = 255) String slug, @Valid @RequestBody CreateBody b, Authentication a) {
        return create(slug, TeamService.Kind.OWNER, b, a);
    }

    @PatchMapping("/{userId}")
    TeamService.MemberView update(@PathVariable @Size(max = 255) String slug, @PathVariable UUID userId, @Valid @RequestBody PatchBody b, Authentication a) {
        UserPrincipal u = p(a); return team.update(slug, userId, new TeamService.PatchMember(b.name(), b.active()), u.userId(), u.role());
    }

    @PostMapping("/{userId}/invite")
    TeamService.CreatedMember resend(@PathVariable @Size(max = 255) String slug, @PathVariable UUID userId, Authentication a) {
        UserPrincipal u = p(a); return team.resendInvite(slug, userId, u.userId(), u.role());
    }

    private ResponseEntity<TeamService.CreatedMember> create(String slug, TeamService.Kind kind, CreateBody b, Authentication a) {
        UserPrincipal u = p(a);
        return ResponseEntity.status(201).body(team.create(slug, kind,
                new TeamService.CreateMember(b.name(), b.email(), b.phone(), b.password()), u.userId(), u.role()));
    }
}
