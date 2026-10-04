package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Organizer-owned team management. ADMIN may act on any organizer; an ORGANIZER owner only on organizers they
 * own (enforced by EventAccessService.requireOrganizerAccess). Staff and managers belong to exactly one organizer
 * (organizer_members rows with role STAFF / EVENT_MANAGER, unique per user - see V19).
 * Passwords are never returned or logged; invite tokens are stored only as SHA-256 hashes.
 */
@Service
@RequiredArgsConstructor
public class TeamService {
    private static final Logger log = LoggerFactory.getLogger(TeamService.class);
    public static final Duration INVITE_TTL = Duration.ofHours(48);
    private static final List<String> TEAM_ROLES = List.of("STAFF", "EVENT_MANAGER");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 ()\\-]{5,29}$");

    public enum Kind {
        STAFF(Enums.UserRole.STAFF, "STAFF", "TEAM_STAFF_CREATED"),
        MANAGER(Enums.UserRole.EVENT_MANAGER, "EVENT_MANAGER", "TEAM_MANAGER_CREATED"),
        OWNER(Enums.UserRole.ORGANIZER, "OWNER", "TEAM_OWNER_CREATED");
        final Enums.UserRole userRole; final String memberRole; final String auditAction;
        Kind(Enums.UserRole u, String m, String a) { userRole = u; memberRole = m; auditAction = a; }
    }

    public record AssignmentView(UUID eventId, String eventName, String gate) {}
    public record MemberView(UUID id, String name, String email, String phone, boolean active, boolean invitePending,
                             Instant createdAt, List<AssignmentView> assignments) {}
    public record EventRef(UUID id, String name, String status, Instant startsAt) {}
    /** events = this organizer's events, so the UI never has to guess which events belong to which organizer. */
    public record TeamView(UUID organizerId, String slug, String name, boolean inviteEmailEnabled,
                           List<EventRef> events, List<MemberView> owners, List<MemberView> staff, List<MemberView> managers) {}
    public record CreateMember(String name, String email, String phone, String password) {}
    public record PatchMember(String name, Boolean active) {}
    public record CreatedMember(MemberView member, String delivery) {}
    public record EventStaffRow(UUID userId, String name, String email, String gate, boolean active) {}
    public record EventManagerRow(UUID userId, String name, String email, boolean active) {}
    public record EventTeam(UUID eventId, String eventName, List<EventStaffRow> staff, List<EventManagerRow> managers) {}

    private final OrganizerRepository organizers;
    private final OrganizerMemberRepository members;
    private final UserRepository users;
    private final UserInviteRepository invites;
    private final RefreshTokenRepository refreshTokens;
    private final EventRepository events;
    private final EventStaffRepository staff;
    private final EventManagerAssignmentRepository managerAssignments;
    private final EventAccessService eventAccess;
    private final AuditService audit;
    private final RateLimitService rateLimits;
    private final PasswordEncoder encoder;
    private final InviteMailer mailer;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    // ---------------------------------------------------------------- access

    private Organizer requireOrganizer(String slug, Long actorId, String role) {
        String clean = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        Optional<Organizer> o = organizers.findBySlug(clean);
        if (o.isEmpty()) {
            // Do not let non-admins probe which organizer slugs exist.
            if ("ADMIN".equals(role)) throw new ApiException(HttpStatus.NOT_FOUND, "ORGANIZER_NOT_FOUND", "Organizer not found");
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this organizer's team");
        }
        eventAccess.requireOrganizerAccess(o.get().getId(), actorId, role);
        return o.get();
    }

    private static void requireTeamManager(String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can manage the team");
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public TeamView list(String slug, Long actorId, String role) {
        Organizer o = requireOrganizer(slug, actorId, role);
        List<OrganizerMember> owners = members.findByOrganizerIdAndRoleInOrderByIdAsc(o.getId(), List.of("OWNER", "ORGANIZER"));
        List<OrganizerMember> staffM = members.findByOrganizerIdAndRoleInOrderByIdAsc(o.getId(), List.of("STAFF"));
        List<OrganizerMember> managerM = members.findByOrganizerIdAndRoleInOrderByIdAsc(o.getId(), List.of("EVENT_MANAGER"));

        Set<Long> ids = new HashSet<>();
        for (var m : owners) ids.add(m.getUserId());
        for (var m : staffM) ids.add(m.getUserId());
        for (var m : managerM) ids.add(m.getUserId());
        Map<Long, User> byId = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Event> orgEvents = events.findByOrganizerIdOrderByStartsAtDesc(o.getId()).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<AssignmentView>> staffAssign = new HashMap<>();
        for (EventStaff es : staff.findByUserIdIn(staffM.stream().map(OrganizerMember::getUserId).toList())) {
            Event e = orgEvents.get(es.getEventId());
            if (e != null) staffAssign.computeIfAbsent(es.getUserId(), k -> new ArrayList<>())
                    .add(new AssignmentView(e.getPublicId(), e.getName(), es.getGate()));
        }
        Map<Long, List<AssignmentView>> mgrAssign = new HashMap<>();
        for (EventManagerAssignment a : managerAssignments.findByUserIdIn(managerM.stream().map(OrganizerMember::getUserId).toList())) {
            Event e = orgEvents.get(a.getEventId());
            if (e != null) mgrAssign.computeIfAbsent(a.getUserId(), k -> new ArrayList<>())
                    .add(new AssignmentView(e.getPublicId(), e.getName(), null));
        }
        List<EventRef> eventRefs = orgEvents.values().stream()
                .map(e -> new EventRef(e.getPublicId(), e.getName(), e.getStatus().name(), e.getStartsAt())).toList();
        return new TeamView(o.getPublicId(), o.getSlug(), o.getName(), mailer.isEnabled(), eventRefs,
                view(owners, byId, Map.of()), view(staffM, byId, staffAssign), view(managerM, byId, mgrAssign));
    }

    private List<MemberView> view(List<OrganizerMember> ms, Map<Long, User> byId, Map<Long, List<AssignmentView>> assignments) {
        Instant now = Instant.now();
        List<MemberView> out = new ArrayList<>();
        for (OrganizerMember m : ms) {
            User u = byId.get(m.getUserId());
            if (u == null) continue;
            out.add(new MemberView(u.getPublicId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isEnabled(),
                    invites.existsByUserIdAndUsedAtIsNullAndExpiresAtAfter(u.getId(), now),
                    u.getCreatedAt(), assignments.getOrDefault(u.getId(), List.of())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public EventTeam eventTeam(UUID eventPublicId, UserPrincipal p) {
        requireTeamManager(p.role());
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role()))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        List<EventStaff> es = staff.findByEventIdOrderByGateAscIdAsc(e.getId());
        List<EventManagerAssignment> ms = managerAssignments.findByEventIdOrderByUserIdAsc(e.getId());
        Set<Long> ids = new HashSet<>();
        es.forEach(x -> ids.add(x.getUserId())); ms.forEach(x -> ids.add(x.getUserId()));
        Map<Long, User> byId = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<EventStaffRow> staffRows = es.stream().filter(x -> byId.containsKey(x.getUserId())).map(x -> {
            User u = byId.get(x.getUserId());
            return new EventStaffRow(u.getPublicId(), u.getFullName(), u.getEmail(), x.getGate(), u.isEnabled());
        }).toList();
        List<EventManagerRow> mgrRows = ms.stream().filter(x -> byId.containsKey(x.getUserId())).map(x -> {
            User u = byId.get(x.getUserId());
            return new EventManagerRow(u.getPublicId(), u.getFullName(), u.getEmail(), u.isEnabled());
        }).toList();
        return new EventTeam(e.getPublicId(), e.getName(), staffRows, mgrRows);
    }

    // ---------------------------------------------------------------- create / update

    public CreatedMember create(String slug, Kind kind, CreateMember req, Long actorId, String role) {
        Organizer o = requireOrganizer(slug, actorId, role);
        if (kind == Kind.OWNER && !"ADMIN".equals(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators can add organizer owners");
        if (!rateLimits.allow("team-create:" + actorId, 30, Duration.ofHours(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many accounts created. Please try again later.");

        String name = cleanName(req.name());
        String email = cleanEmail(req.email());
        String phone = cleanPhone(req.phone());
        boolean useInvite = req.password() == null || req.password().isBlank();
        if (!useInvite && (req.password().length() < 12 || req.password().length() > 128))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password must be between 12 and 128 characters");
        if (useInvite && !mailer.isEnabled())
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REQUIRED",
                    "Email invites are not configured on this server. Set an initial password instead; the member must change it at first sign-in.");

        String rawToken = useInvite ? newToken() : null;
        User created;
        try {
            created = tx.execute(status -> {
                if (users.findByEmailIgnoreCase(email).isPresent())
                    throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "An account with this email already exists on the platform");
                User u = new User();
                u.setEmail(email); u.setFullName(name); u.setPhone(phone);
                // Invite accounts get an unguessable password nobody knows until the invite is accepted.
                u.setPasswordHash(encoder.encode(useInvite ? newToken() : req.password()));
                u.setRole(kind.userRole); u.setEnabled(true); u.setMustChangePassword(true);
                u = users.saveAndFlush(u);
                OrganizerMember m = new OrganizerMember();
                m.setOrganizerId(o.getId()); m.setUserId(u.getId()); m.setRole(kind.memberRole);
                members.saveAndFlush(m);
                if (useInvite) issueInvite(u, rawToken, actorId);
                audit.log(actorId, kind.auditAction, "USER", u.getPublicId().toString(), o.getPublicId().toString());
                return u;
            });
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "An account with this email already exists on the platform");
        }
        String delivery = "PASSWORD_SET";
        if (useInvite) delivery = deliver(created, o, rawToken);
        MemberView mv = new MemberView(created.getPublicId(), created.getFullName(), created.getEmail(), created.getPhone(),
                true, useInvite, created.getCreatedAt(), List.of());
        return new CreatedMember(mv, delivery);
    }

    /** Re-issues an invite (also works as an access reset: the old link stops working). */
    public CreatedMember resendInvite(String slug, UUID userPublicId, Long actorId, String role) {
        Organizer o = requireOrganizer(slug, actorId, role);
        if (!mailer.isEnabled())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVITES_UNAVAILABLE", "Email invites are not configured on this server");
        if (!rateLimits.allow("team-invite:" + actorId, 30, Duration.ofHours(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many invites sent. Please try again later.");
        User u = requireMember(o, userPublicId);
        if (!u.isEnabled()) throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_DEACTIVATED", "Reactivate this account before sending an invite");
        String raw = newToken();
        tx.executeWithoutResult(s -> { issueInvite(u, raw, actorId); audit.log(actorId, "TEAM_INVITE_SENT", "USER", u.getPublicId().toString(), o.getPublicId().toString()); });
        String delivery = deliver(u, o, raw);
        return new CreatedMember(new MemberView(u.getPublicId(), u.getFullName(), u.getEmail(), u.getPhone(), true, true, u.getCreatedAt(), List.of()), delivery);
    }

    @Transactional
    public MemberView update(String slug, UUID userPublicId, PatchMember patch, Long actorId, String role) {
        Organizer o = requireOrganizer(slug, actorId, role);
        User u = requireMember(o, userPublicId);
        if (patch.name() == null && patch.active() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTHING_TO_UPDATE", "Provide a name or an active flag");
        if (patch.name() != null) {
            u.setFullName(cleanName(patch.name()));
            audit.log(actorId, "TEAM_MEMBER_RENAMED", "USER", u.getPublicId().toString(), o.getPublicId().toString());
        }
        if (patch.active() != null && patch.active() != u.isEnabled()) {
            u.setEnabled(patch.active());
            if (!patch.active()) {
                // Deactivation takes effect immediately: JwtAuthFilter rejects disabled users on every request,
                // and we also kill refresh tokens and any open invite.
                Instant now = Instant.now();
                refreshTokens.revokeAllActiveByUserId(u.getId(), now);
                invites.closeOpenInvites(u.getId(), now);
            }
            audit.log(actorId, patch.active() ? "TEAM_MEMBER_REACTIVATED" : "TEAM_MEMBER_DEACTIVATED", "USER", u.getPublicId().toString(), o.getPublicId().toString());
        }
        users.save(u);
        return new MemberView(u.getPublicId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isEnabled(), false, u.getCreatedAt(), List.of());
    }

    // ---------------------------------------------------------------- gate / manager assignment rules

    /**
     * Guarantees the person being assigned belongs to the event's organizer. A person on another organizer's team
     * is rejected. A legacy account with no organizer yet is adopted only when the actor is a platform ADMIN.
     */
    @Transactional
    public void requireTeamMemberOf(Long organizerId, User u, String memberRole, String actorRole) {
        if (!u.isEnabled())
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_DEACTIVATED", "This account is deactivated. Reactivate it before assigning it to an event");
        Optional<OrganizerMember> existing = members.findByUserId(u.getId()).stream()
                .filter(m -> TEAM_ROLES.contains(m.getRole())).findFirst();
        if (existing.isPresent()) {
            if (!existing.get().getOrganizerId().equals(organizerId))
                throw new ApiException(HttpStatus.CONFLICT, "DIFFERENT_ORGANIZER",
                        "This person belongs to a different organizer and cannot be assigned to this event");
            return;
        }
        if (!"ADMIN".equals(actorRole))
            throw new ApiException(HttpStatus.CONFLICT, "NOT_IN_ORGANIZER_TEAM",
                    "This person is not on your team. Add them under Team first");
        OrganizerMember m = new OrganizerMember();
        m.setOrganizerId(organizerId); m.setUserId(u.getId()); m.setRole(memberRole);
        try { members.saveAndFlush(m); }
        catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DIFFERENT_ORGANIZER", "This person was just linked to another organizer");
        }
    }

    @Transactional
    public void removeStaffAssignment(UUID eventPublicId, String email, Long actorId, String role) {
        Event e = requireEventForTeamChange(eventPublicId, actorId, role);
        User u = users.findByEmailIgnoreCase(email.trim()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Staff user not found"));
        staff.deleteByEventIdAndUserId(e.getId(), u.getId()); // idempotent
        audit.log(actorId, "STAFF_UNASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    @Transactional
    public void changeStaffGate(UUID eventPublicId, String email, String gate, Long actorId, String role) {
        Event e = requireEventForTeamChange(eventPublicId, actorId, role);
        User u = users.findByEmailIgnoreCase(email.trim()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Staff user not found"));
        EventStaff s = staff.findByEventIdAndUserId(e.getId(), u.getId()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "This staff member is not assigned to this event"));
        s.setGate(cleanGate(gate));
        staff.save(s);
        audit.log(actorId, "STAFF_GATE_CHANGED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    private Event requireEventForTeamChange(UUID eventPublicId, Long actorId, String role) {
        requireTeamManager(role);
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        return e;
    }

    public String cleanGate(String gate) {
        String g = gate == null ? "" : gate.trim().replaceAll("\\s+", " ");
        if (g.isEmpty() || g.length() > 80 || g.codePoints().anyMatch(Character::isISOControl))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GATE", "Gate name must be 1-80 characters without control characters");
        return g;
    }

    // ---------------------------------------------------------------- helpers

    private User requireMember(Organizer o, UUID userPublicId) {
        User u = users.findByPublicId(userPublicId).orElseThrow(notFound());
        OrganizerMember m = members.findByOrganizerIdAndUserId(o.getId(), u.getId()).orElseThrow(notFound());
        if (!TEAM_ROLES.contains(m.getRole())) throw notFound().get(); // owners are not editable here
        return u;
    }

    private static java.util.function.Supplier<ApiException> notFound() {
        // Same answer for "no such user" and "user of another organizer" - no cross-organizer probing.
        return () -> new ApiException(HttpStatus.NOT_FOUND, "TEAM_MEMBER_NOT_FOUND", "Team member not found");
    }

    private void issueInvite(User u, String rawToken, Long actorId) {
        Instant now = Instant.now();
        invites.closeOpenInvites(u.getId(), now);
        UserInvite i = new UserInvite();
        i.setUserId(u.getId()); i.setTokenHash(sha256(rawToken)); i.setExpiresAt(now.plus(INVITE_TTL)); i.setCreatedBy(actorId);
        invites.save(i);
    }

    private String deliver(User u, Organizer o, String rawToken) {
        try {
            mailer.sendInvite(u.getEmail(), u.getFullName(), o.getName(), mailer.inviteLink(rawToken), Instant.now().plus(INVITE_TTL));
            return "INVITE_SENT";
        } catch (Exception e) {
            // Never log the link or token. The member exists; the organizer can use "Resend invite".
            log.warn("Invite email could not be sent for user {} ({})", u.getPublicId(), e.getClass().getSimpleName());
            return "INVITE_FAILED";
        }
    }

    private String newToken() {
        byte[] b = new byte[32]; random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String v) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    private static String cleanName(String n) {
        String v = n == null ? "" : n.trim().replaceAll("\\s+", " ");
        if (v.length() < 2 || v.length() > 120 || v.codePoints().anyMatch(Character::isISOControl))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Name must be between 2 and 120 characters");
        return v;
    }

    private static String cleanEmail(String e) {
        String v = e == null ? "" : e.trim().toLowerCase(Locale.ROOT);
        if (v.length() > 255 || !v.matches("^[^\\s@<>\"',;]+@[^\\s@<>\"',;]+\\.[^\\s@<>\"',;]+$"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter a valid email address");
        return v;
    }

    private static String cleanPhone(String p) {
        if (p == null || p.isBlank()) return null;
        String v = p.trim();
        if (!PHONE.matcher(v).matches()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a valid phone number");
        return v;
    }
}
