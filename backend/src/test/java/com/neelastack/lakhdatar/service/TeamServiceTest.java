package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import com.neelastack.lakhdatar.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cross-organizer isolation, role checks, deactivation and idempotent assignment for the organizer-owned team. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamServiceTest {
    @Mock OrganizerRepository organizers;
    @Mock OrganizerMemberRepository members;
    @Mock UserRepository users;
    @Mock UserInviteRepository invites;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock EventRepository events;
    @Mock EventStaffRepository staff;
    @Mock EventManagerAssignmentRepository managerAssignments;
    @Mock AuditService audit;
    @Mock RateLimitService rateLimits;
    @Mock PasswordEncoder encoder;
    @Mock InviteMailer mailer;
    @Mock TransactionTemplate tx;

    TeamService service;

    static final long ORG_A = 1L, ORG_B = 2L, OWNER_A = 10L, OWNER_B = 20L, ADMIN = 99L;

    @BeforeEach void setUp() {
        EventAccessService access = new EventAccessService(members, managerAssignments, events);
        service = new TeamService(organizers, members, users, invites, refreshTokens, events, staff,
                managerAssignments, access, audit, rateLimits, encoder, mailer, tx);
        when(organizers.findBySlug("org-a")).thenReturn(Optional.of(org(ORG_A, "org-a")));
        when(organizers.findBySlug("org-b")).thenReturn(Optional.of(org(ORG_B, "org-b")));
        when(members.findByOrganizerIdAndUserId(ORG_A, OWNER_A)).thenReturn(Optional.of(member(ORG_A, OWNER_A, "OWNER")));
        when(members.findByOrganizerIdAndUserId(ORG_B, OWNER_B)).thenReturn(Optional.of(member(ORG_B, OWNER_B, "OWNER")));
        when(rateLimits.allow(anyString(), anyInt(), any())).thenReturn(true);
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<?>) i.getArgument(0)).doInTransaction(null));
    }

    // ------------------------------------------------------------------ authorization / isolation

    @Test void organizerOwnerCannotReadAnotherOrganizersTeam() {
        ApiException e = assertThrows(ApiException.class, () -> service.list("org-b", OWNER_A, "ORGANIZER"));
        assertEquals(HttpStatus.FORBIDDEN, e.status());
        verifyNoInteractions(users);
    }

    @Test void organizerOwnerCannotCreateInAnotherOrganizer() {
        ApiException e = assertThrows(ApiException.class, () -> service.create("org-b", TeamService.Kind.STAFF,
                new TeamService.CreateMember("Gate One", "gate@example.com", null, "a-very-long-password"), OWNER_A, "ORGANIZER"));
        assertEquals(HttpStatus.FORBIDDEN, e.status());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void organizerOwnerCanReadOwnTeamAndAdminCanReadAny() {
        stubEmptyTeam();
        assertEquals("org-a", service.list("org-a", OWNER_A, "ORGANIZER").slug());
        assertEquals("org-b", service.list("org-b", ADMIN, "ADMIN").slug());
    }

    @Test void nonOwnerRolesAreForbidden() {
        for (String role : List.of("EVENT_MANAGER", "STAFF", "FINANCE", "SUPPORT", "CUSTOMER")) {
            ApiException e = assertThrows(ApiException.class, () -> service.list("org-a", 55L, role), role);
            assertEquals(HttpStatus.FORBIDDEN, e.status(), role);
        }
    }

    @Test void organizerMembershipWithNonOwnerRoleIsForbidden() {
        when(members.findByOrganizerIdAndUserId(ORG_A, 56L)).thenReturn(Optional.of(member(ORG_A, 56L, "EVENT_MANAGER")));
        assertThrows(ApiException.class, () -> service.list("org-a", 56L, "ORGANIZER"));
    }

    @Test void unknownSlugIsForbiddenForOrganizerAndNotFoundForAdmin() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.list("nope", OWNER_A, "ORGANIZER")).status());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.list("nope", ADMIN, "ADMIN")).status());
    }

    @Test void onlyAdminCanAddOrganizerOwners() {
        ApiException e = assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.OWNER,
                new TeamService.CreateMember("Second Owner", "own@example.com", null, "a-very-long-password"), OWNER_A, "ORGANIZER"));
        assertEquals(HttpStatus.FORBIDDEN, e.status());
        verify(users, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ creation

    @Test void passwordFlowForcesChangeAndNeverReturnsOrLogsThePassword() {
        String pw = "initial-secret-pass";
        when(mailer.isEnabled()).thenReturn(false);
        when(encoder.encode(pw)).thenReturn("HASHED");
        when(users.findByEmailIgnoreCase("gate@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); u.setId(5L); return u; });
        when(members.saveAndFlush(any(OrganizerMember.class))).thenAnswer(i -> i.getArgument(0));

        TeamService.CreatedMember out = service.create("org-a", TeamService.Kind.STAFF,
                new TeamService.CreateMember("  Gate   One ", "Gate@Example.com", "+91 98765 43210", pw), OWNER_A, "ORGANIZER");

        ArgumentCaptor<User> u = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(u.capture());
        assertEquals("gate@example.com", u.getValue().getEmail());
        assertEquals("Gate One", u.getValue().getFullName());
        assertEquals(Enums.UserRole.STAFF, u.getValue().getRole());
        assertEquals("HASHED", u.getValue().getPasswordHash());
        assertTrue(u.getValue().isMustChangePassword());
        ArgumentCaptor<OrganizerMember> m = ArgumentCaptor.forClass(OrganizerMember.class);
        verify(members).saveAndFlush(m.capture());
        assertEquals(ORG_A, m.getValue().getOrganizerId());
        assertEquals("STAFF", m.getValue().getRole());
        assertEquals("PASSWORD_SET", out.delivery());
        assertFalse(out.toString().contains(pw));
        verify(audit).log(eq(OWNER_A), eq("TEAM_STAFF_CREATED"), eq("USER"), anyString(), anyString());
    }

    @Test void inviteIsRequiredToBeConfiguredWhenNoPasswordIsGiven() {
        when(mailer.isEnabled()).thenReturn(false);
        ApiException e = assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.MANAGER,
                new TeamService.CreateMember("Mgr One", "m@example.com", null, null), OWNER_A, "ORGANIZER"));
        assertEquals("PASSWORD_REQUIRED", e.code());
        verify(users, never()).saveAndFlush(any());
    }

    @Test void inviteFlowStoresOnlyAHashAndMailsTheLink() {
        when(mailer.isEnabled()).thenReturn(true);
        when(mailer.inviteLink(anyString())).thenAnswer(i -> "https://x.test/accept-invite?token=" + i.getArgument(0));
        when(encoder.encode(anyString())).thenReturn("UNUSABLE");
        when(users.findByEmailIgnoreCase("m@example.com")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); u.setId(6L); return u; });
        when(members.saveAndFlush(any(OrganizerMember.class))).thenAnswer(i -> i.getArgument(0));

        TeamService.CreatedMember out = service.create("org-a", TeamService.Kind.MANAGER,
                new TeamService.CreateMember("Mgr One", "m@example.com", null, null), OWNER_A, "ORGANIZER");

        assertEquals("INVITE_SENT", out.delivery());
        ArgumentCaptor<UserInvite> inv = ArgumentCaptor.forClass(UserInvite.class);
        verify(invites).save(inv.capture());
        assertEquals(64, inv.getValue().getTokenHash().length());
        assertTrue(inv.getValue().getExpiresAt().isAfter(java.time.Instant.now().plus(java.time.Duration.ofHours(47))));
        assertTrue(inv.getValue().getExpiresAt().isBefore(java.time.Instant.now().plus(java.time.Duration.ofHours(49))));
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendInvite(eq("m@example.com"), eq("Mgr One"), anyString(), link.capture(), any());
        String raw = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertNotEquals(raw, inv.getValue().getTokenHash(), "the raw token must never be persisted");
        assertEquals(TeamService.sha256(raw), inv.getValue().getTokenHash());
    }

    @Test void emailMustBeUniqueAcrossThePlatform() {
        when(mailer.isEnabled()).thenReturn(false);
        when(users.findByEmailIgnoreCase("taken@example.com")).thenReturn(Optional.of(new User()));
        ApiException e = assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.STAFF,
                new TeamService.CreateMember("Gate One", "taken@example.com", null, "a-very-long-password"), OWNER_A, "ORGANIZER"));
        assertEquals(HttpStatus.CONFLICT, e.status());
        assertEquals("USER_EXISTS", e.code());
    }

    @Test void createRejectsShortPasswordsAndBadInput() {
        assertEquals("INVALID_PASSWORD", assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.STAFF,
                new TeamService.CreateMember("Gate One", "g@example.com", null, "short"), OWNER_A, "ORGANIZER")).code());
        assertEquals("INVALID_EMAIL", assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.STAFF,
                new TeamService.CreateMember("Gate One", "not-an-email", null, "a-very-long-password"), OWNER_A, "ORGANIZER")).code());
        assertEquals("INVALID_PHONE", assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.STAFF,
                new TeamService.CreateMember("Gate One", "g@example.com", "abc", "a-very-long-password"), OWNER_A, "ORGANIZER")).code());
    }

    @Test void creationIsRateLimited() {
        when(rateLimits.allow(startsWith("team-create:"), anyInt(), any())).thenReturn(false);
        ApiException e = assertThrows(ApiException.class, () -> service.create("org-a", TeamService.Kind.STAFF,
                new TeamService.CreateMember("Gate One", "g@example.com", null, "a-very-long-password"), OWNER_A, "ORGANIZER"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.status());
    }

    // ------------------------------------------------------------------ deactivate / reactivate

    @Test void deactivationDisablesTheUserAndKillsSessionsAndInvites() {
        User u = user(7L, Enums.UserRole.STAFF, true);
        when(users.findByPublicId(u.getPublicId())).thenReturn(Optional.of(u));
        when(members.findByOrganizerIdAndUserId(ORG_A, 7L)).thenReturn(Optional.of(member(ORG_A, 7L, "STAFF")));

        service.update("org-a", u.getPublicId(), new TeamService.PatchMember(null, false), OWNER_A, "ORGANIZER");

        assertFalse(u.isEnabled());
        verify(refreshTokens).revokeAllActiveByUserId(eq(7L), any());
        verify(invites).closeOpenInvites(eq(7L), any());
        verify(audit).log(eq(OWNER_A), eq("TEAM_MEMBER_DEACTIVATED"), eq("USER"), anyString(), anyString());
    }

    @Test void reactivationAndRenameWork() {
        User u = user(7L, Enums.UserRole.STAFF, false);
        when(users.findByPublicId(u.getPublicId())).thenReturn(Optional.of(u));
        when(members.findByOrganizerIdAndUserId(ORG_A, 7L)).thenReturn(Optional.of(member(ORG_A, 7L, "STAFF")));

        TeamService.MemberView v = service.update("org-a", u.getPublicId(), new TeamService.PatchMember("New Name", true), OWNER_A, "ORGANIZER");

        assertTrue(u.isEnabled());
        assertEquals("New Name", v.name());
        verify(refreshTokens, never()).revokeAllActiveByUserId(anyLong(), any());
    }

    @Test void memberOfAnotherOrganizerLooksLikeNotFound() {
        User foreign = user(8L, Enums.UserRole.STAFF, true);
        when(users.findByPublicId(foreign.getPublicId())).thenReturn(Optional.of(foreign));
        when(members.findByOrganizerIdAndUserId(ORG_A, 8L)).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class, () ->
                service.update("org-a", foreign.getPublicId(), new TeamService.PatchMember(null, false), OWNER_A, "ORGANIZER"));
        assertEquals(HttpStatus.NOT_FOUND, e.status());
        assertTrue(foreign.isEnabled(), "must not be touched");
    }

    @Test void ownersCannotBeEditedThroughTheMemberEndpoint() {
        User owner = user(OWNER_A, Enums.UserRole.ORGANIZER, true);
        when(users.findByPublicId(owner.getPublicId())).thenReturn(Optional.of(owner));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () ->
                service.update("org-a", owner.getPublicId(), new TeamService.PatchMember(null, false), OWNER_A, "ORGANIZER")).status());
        assertTrue(owner.isEnabled());
    }

    // ------------------------------------------------------------------ assignment rules

    @Test void assigningSomeoneFromAnotherOrganizerFailsWithAClearMessage() {
        User u = user(7L, Enums.UserRole.STAFF, true);
        when(members.findByUserId(7L)).thenReturn(List.of(member(ORG_B, 7L, "STAFF")));
        ApiException e = assertThrows(ApiException.class, () -> service.requireTeamMemberOf(ORG_A, u, "STAFF", "ORGANIZER"));
        assertEquals(HttpStatus.CONFLICT, e.status());
        assertEquals("DIFFERENT_ORGANIZER", e.code());
        assertTrue(e.getMessage().contains("different organizer"));
        verify(members, never()).saveAndFlush(any());
    }

    @Test void assigningTheSameOrganizersMemberIsIdempotentAndWritesNothing() {
        User u = user(7L, Enums.UserRole.STAFF, true);
        when(members.findByUserId(7L)).thenReturn(List.of(member(ORG_A, 7L, "STAFF")));
        service.requireTeamMemberOf(ORG_A, u, "STAFF", "ORGANIZER");
        service.requireTeamMemberOf(ORG_A, u, "STAFF", "ORGANIZER");
        verify(members, never()).saveAndFlush(any());
    }

    @Test void legacyUnlinkedAccountIsRejectedForOrganizerButAdoptedByAdmin() {
        User u = user(7L, Enums.UserRole.STAFF, true);
        when(members.findByUserId(7L)).thenReturn(List.of());
        assertEquals("NOT_IN_ORGANIZER_TEAM", assertThrows(ApiException.class, () -> service.requireTeamMemberOf(ORG_A, u, "STAFF", "ORGANIZER")).code());
        verify(members, never()).saveAndFlush(any());

        service.requireTeamMemberOf(ORG_A, u, "STAFF", "ADMIN");
        ArgumentCaptor<OrganizerMember> m = ArgumentCaptor.forClass(OrganizerMember.class);
        verify(members).saveAndFlush(m.capture());
        assertEquals(ORG_A, m.getValue().getOrganizerId());
        assertEquals("STAFF", m.getValue().getRole());
    }

    @Test void deactivatedAccountsCannotBeAssigned() {
        User u = user(7L, Enums.UserRole.STAFF, false);
        ApiException e = assertThrows(ApiException.class, () -> service.requireTeamMemberOf(ORG_A, u, "STAFF", "ADMIN"));
        assertEquals("ACCOUNT_DEACTIVATED", e.code());
    }

    // ------------------------------------------------------------------ gate remove / change

    private Event stubEventOfOrgA(UUID publicId) {
        Event e = new Event(); e.setId(42L); e.setOrganizerId(ORG_A);
        when(events.findByPublicId(publicId)).thenReturn(Optional.of(e));
        when(events.findById(42L)).thenReturn(Optional.of(e));
        return e;
    }

    @Test void removingAGateAssignmentIsIdempotent() {
        UUID eventId = UUID.randomUUID(); stubEventOfOrgA(eventId);
        User u = user(7L, Enums.UserRole.STAFF, true);
        when(users.findByEmailIgnoreCase("gate@example.com")).thenReturn(Optional.of(u));

        service.removeStaffAssignment(eventId, "gate@example.com", OWNER_A, "ORGANIZER");
        service.removeStaffAssignment(eventId, "gate@example.com", OWNER_A, "ORGANIZER");

        verify(staff, times(2)).deleteByEventIdAndUserId(42L, 7L);
        verify(audit, times(2)).log(eq(OWNER_A), eq("STAFF_UNASSIGNED"), eq("EVENT"), anyString(), anyString());
    }

    @Test void anotherOrganizersOwnerCannotTouchThisEventsGates() {
        UUID eventId = UUID.randomUUID(); stubEventOfOrgA(eventId);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () ->
                service.removeStaffAssignment(eventId, "gate@example.com", OWNER_B, "ORGANIZER")).status());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () ->
                service.changeStaffGate(eventId, "gate@example.com", "Gate 2", OWNER_B, "ORGANIZER")).status());
        verify(staff, never()).deleteByEventIdAndUserId(anyLong(), anyLong());
        verify(staff, never()).save(any());
    }

    @Test void eventManagersAndStaffCannotChangeGates() {
        UUID eventId = UUID.randomUUID(); stubEventOfOrgA(eventId);
        for (String role : List.of("EVENT_MANAGER", "STAFF", "FINANCE")) {
            assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () ->
                    service.changeStaffGate(eventId, "gate@example.com", "Gate 2", 77L, role)).status(), role);
        }
        verify(staff, never()).save(any());
    }

    @Test void changingGateUpdatesAnExistingAssignmentOnly() {
        UUID eventId = UUID.randomUUID(); stubEventOfOrgA(eventId);
        User u = user(7L, Enums.UserRole.STAFF, true);
        when(users.findByEmailIgnoreCase("gate@example.com")).thenReturn(Optional.of(u));
        when(staff.findByEventIdAndUserId(42L, 7L)).thenReturn(Optional.empty());
        assertEquals("ASSIGNMENT_NOT_FOUND", assertThrows(ApiException.class, () ->
                service.changeStaffGate(eventId, "gate@example.com", "Gate 2", OWNER_A, "ORGANIZER")).code());

        EventStaff es = new EventStaff(); es.setEventId(42L); es.setUserId(7L); es.setGate("Main Gate");
        when(staff.findByEventIdAndUserId(42L, 7L)).thenReturn(Optional.of(es));
        service.changeStaffGate(eventId, "gate@example.com", "  VIP   Gate ", OWNER_A, "ORGANIZER");
        assertEquals("VIP Gate", es.getGate());
        verify(staff).save(es);
    }

    @Test void gateNamesAreValidated() {
        assertEquals("North Gate", service.cleanGate("  North   Gate "));
        assertThrows(ApiException.class, () -> service.cleanGate("   "));
        assertThrows(ApiException.class, () -> service.cleanGate("x".repeat(81)));
        assertThrows(ApiException.class, () -> service.cleanGate("bad\u0007gate"));
    }

    // ------------------------------------------------------------------ helpers

    private void stubEmptyTeam() {
        when(members.findByOrganizerIdAndRoleInOrderByIdAsc(anyLong(), any())).thenReturn(List.of());
        when(users.findAllById(any())).thenReturn(List.of());
        when(events.findByOrganizerIdOrderByStartsAtDesc(anyLong())).thenReturn(List.of());
        when(staff.findByUserIdIn(any())).thenReturn(List.of());
        when(managerAssignments.findByUserIdIn(any())).thenReturn(List.of());
    }

    private static Organizer org(long id, String slug) {
        Organizer o = new Organizer(); o.setId(id); o.setSlug(slug); o.setName(slug.toUpperCase()); return o;
    }
    private static OrganizerMember member(long orgId, long userId, String role) {
        OrganizerMember m = new OrganizerMember(); m.setOrganizerId(orgId); m.setUserId(userId); m.setRole(role); return m;
    }
    private static User user(long id, Enums.UserRole role, boolean enabled) {
        User u = new User(); u.setId(id); u.setEmail("u" + id + "@example.com"); u.setFullName("User " + id);
        u.setRole(role); u.setEnabled(enabled); u.setPasswordHash("x"); return u;
    }
}
