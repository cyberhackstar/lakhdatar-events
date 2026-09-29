package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final OrganizerMemberRepository members;
    private final EventManagerAssignmentRepository managerAssignments;
    private final TicketTypeRepository ticketTypes;
    private final TicketRepository tickets;
    private final PaymentRepository payments;
    private final TicketCheckinRepository checkins;
    private final EventStaffRepository staff;
    private final UserRepository users;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final EventAccessService eventAccess;

    public record EventSummary(UUID id, String slug, String name, String status,
                               java.time.Instant startsAt, int ticketsSold, int ticketsCheckedIn,
                               long revenueMinor) {}

    public record Dashboard(List<EventSummary> events, int totalSold, int totalCheckedIn,
                            long totalRevenueMinor) {}

    public record ManagerView(UUID userId, String email, String fullName, String role, String eventName) {}
    public record ManagerTicketType(UUID id, String name, long priceMinorUnits, int availableQuantity, String status) {}

    public Dashboard dashboard(UserPrincipal p) {
        if (p == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");

        List<Event> list = switch (p.role()) {
            case "ADMIN" -> events.findAllByOrderByStartsAtDesc();
            case "EVENT_MANAGER" -> events.findAllByManagerUserIdOrderByStartsAtDesc(p.userId());
            case "ORGANIZER" -> events.findAllByOrderByStartsAtDesc().stream()
                    .filter(e -> e.getOrganizerId() != null && canViewOrganizer(e.getOrganizerId(), p))
                    .toList();
            default -> events.findAllByOrderByStartsAtDesc().stream()
                    .filter(e -> e.getOrganizerId() != null && canViewOrganizer(e.getOrganizerId(), p))
                    .toList();
        };

        int sold = 0;
        int checked = 0;
        long revenue = 0;
        List<EventSummary> out = new ArrayList<>();
        for (Event e : list) {
            int s = ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId()).stream()
                    .mapToInt(TicketType::getSoldQuantity).sum();
            long c = checkins.countAcceptedForEvent(e.getId());
            long r = payments.sumSuccessfulByEventId(e.getId(),
                    List.of(Enums.PaymentStatus.CAPTURED, Enums.PaymentStatus.COMPLETED));
            sold += s;
            checked += (int) c;
            revenue += r;
            out.add(new EventSummary(e.getPublicId(), e.getSlug(), e.getName(), e.getStatus().name(),
                    e.getStartsAt(), s, (int) c, r));
        }
        return new Dashboard(out, sold, checked, revenue);
    }

    public List<EventSummary> events(UserPrincipal p) {
        return dashboard(p).events();
    }

    @Transactional
    public void assignStaff(UUID eventPublicId, String email, String gate, Long actorId, String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can manage event staff");
        }
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        User u = users.findByEmailIgnoreCase(email.trim()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Staff user not found"));
        if (u.getRole() != Enums.UserRole.STAFF) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STAFF_ROLE", "Only STAFF users can be assigned to event gates");
        }
        EventStaff s = staff.findByEventIdAndUserId(e.getId(), u.getId()).orElseGet(EventStaff::new);
        s.setEventId(e.getId());
        s.setUserId(u.getId());
        s.setGate(gate.trim());
        staff.save(s);
        audit.log(actorId, "STAFF_ASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    public void createStaff(String email, String name, String password, Long actorId, String role) {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an administrator can provision staff users");
        }
        createUser(email, name, password, Enums.UserRole.STAFF, actorId, "STAFF_CREATED");
    }

    public void createManager(String email, String name, String password, Long actorId, String role) {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an administrator can provision event managers");
        }
        createUser(email, name, password, Enums.UserRole.EVENT_MANAGER, actorId, "EVENT_MANAGER_CREATED");
    }

    @Transactional
    public void assignManager(UUID eventPublicId, String email, Long actorId, String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can assign event managers");
        }
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        User u = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Event manager account not found"));
        if (u.getRole() != Enums.UserRole.EVENT_MANAGER) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_MANAGER_ROLE", "The selected account is not an EVENT_MANAGER");
        }
        if (!members.existsByOrganizerIdAndUserId(e.getOrganizerId(), u.getId())) {
            OrganizerMember member = new OrganizerMember();
            member.setOrganizerId(e.getOrganizerId());
            member.setUserId(u.getId());
            member.setRole("EVENT_MANAGER");
            members.save(member);
        }
        if (!managerAssignments.existsByEventIdAndUserId(e.getId(), u.getId())) {
            EventManagerAssignment assignment = new EventManagerAssignment();
            assignment.setEventId(e.getId());
            assignment.setUserId(u.getId());
            managerAssignments.save(assignment);
        }
        audit.log(actorId, "EVENT_MANAGER_ASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    @Transactional
    public void unassignManager(UUID eventPublicId, String email, Long actorId, String role) {
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators or organizer owners can remove event managers");
        }
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), actorId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        User u = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Event manager account not found"));
        managerAssignments.deleteByEventIdAndUserId(e.getId(), u.getId());
        audit.log(actorId, "EVENT_MANAGER_UNASSIGNED", "EVENT", e.getPublicId().toString(), u.getPublicId().toString());
    }

    public List<ManagerTicketType> ticketTypesForEvent(UUID eventPublicId, UserPrincipal p) {
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        return ticketTypes.findByEventIdOrderByPriceMinorUnitsAsc(e.getId()).stream()
                .map(t -> new ManagerTicketType(t.getPublicId(), t.getName(), t.getPriceMinorUnits(), t.availableQuantity(), t.getStatus().name()))
                .toList();
    }

    public List<ManagerView> managersForEvent(UUID eventPublicId, UserPrincipal p) {
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        }
        List<ManagerView> out = new ArrayList<>();
        for (EventManagerAssignment a : managerAssignments.findByEventIdOrderByUserIdAsc(e.getId())) {
            users.findById(a.getUserId()).ifPresent(u -> out.add(new ManagerView(u.getPublicId(), u.getEmail(), u.getFullName(), u.getRole().name(), e.getName())));
        }
        return out;
    }

    public String attendeesCsv(UUID eventPublicId, UserPrincipal p) {
        Event e = events.findByPublicId(eventPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(e.getId(), p.userId(), p.role()) && !canViewOrganizer(e.getOrganizerId(), p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized");
        }
        StringBuilder b = new StringBuilder("ticket_number,attendee_name,ticket_status,ticket_source,issued_by,created_at,checked_in_at\n");
        for (Ticket t : tickets.findByEventIdOrderByTicketNumberAsc(e.getId())) {
            String issuer = "";
            if (t.getIssuedByUserId() != null) issuer = users.findById(t.getIssuedByUserId()).map(User::getEmail).orElse("");
            b.append(csv(t.getTicketNumber())).append(',')
                    .append(csv(t.getAttendeeName())).append(',')
                    .append(t.getStatus()).append(',')
                    .append(t.getSource()).append(',')
                    .append(csv(issuer)).append(',')
                    .append(t.getCreatedAt()).append(',')
                    .append(t.getCheckedInAt() == null ? "" : t.getCheckedInAt()).append('\n');
        }
        return b.toString();
    }

    private void createUser(String email, String name, String password, Enums.UserRole targetRole,
                            Long actorId, String auditAction) {
        String normalized = email.trim().toLowerCase();
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "User already exists");
        }
        User u = new User();
        u.setEmail(normalized);
        u.setFullName(name.trim());
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(targetRole);
        u.setEnabled(true);
        users.save(u);
        audit.log(actorId, auditAction, "USER", u.getPublicId().toString(), null);
    }

    private boolean canViewOrganizer(Long organizerId, UserPrincipal p) {
        if (p == null || p.userId() == null) return false;
        if ("ADMIN".equals(p.role())) return true;
        return members.findByOrganizerIdAndUserId(organizerId, p.userId()).map(m -> {
            if ("FINANCE".equals(p.role())) return "OWNER".equalsIgnoreCase(m.getRole()) || "FINANCE".equalsIgnoreCase(m.getRole());
            if ("SUPPORT".equals(p.role())) return "OWNER".equalsIgnoreCase(m.getRole()) || "SUPPORT".equalsIgnoreCase(m.getRole());
            return "OWNER".equalsIgnoreCase(m.getRole()) || "ORGANIZER".equalsIgnoreCase(m.getRole());
        }).orElse(false);
    }

    private String csv(String s) {
        String value = s == null ? "" : s;
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
