package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.EventManagerAssignmentRepository;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrganizerMemberRepository;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventAccessService {
    private final OrganizerMemberRepository members;
    private final EventManagerAssignmentRepository managerAssignments;
    private final EventRepository events;

    /** Organizer-level access is intentionally limited to ADMIN/OWNER-style users. */
    public boolean canManage(Long userId, String role, Long organizerId) {
        if (userId == null) return false;
        if ("ADMIN".equals(role)) return true;
        if (!"ORGANIZER".equals(role)) return false;
        return members.findByOrganizerIdAndUserId(organizerId, userId)
                .map(m -> "OWNER".equalsIgnoreCase(m.getRole()) || "ORGANIZER".equalsIgnoreCase(m.getRole()))
                .orElse(false);
    }

    /**
     * Event authorization is deliberately stricter for EVENT_MANAGER:
     * organizer membership alone is never sufficient. The manager must be
     * explicitly assigned to this event.
     */
    public boolean canManageEvent(UserPrincipal p, Long eventId) {
        if (p == null || eventId == null) return false;
        if ("ADMIN".equals(p.role())) return true;

        Event e = events.findById(eventId).orElse(null);
        if (e == null) return false;

        if ("EVENT_MANAGER".equals(p.role())) {
            return managerAssignments.existsByEventIdAndUserId(eventId, p.userId());
        }

        if ("ORGANIZER".equals(p.role())) {
            return members.findByOrganizerIdAndUserId(e.getOrganizerId(), p.userId())
                    .map(m -> "OWNER".equalsIgnoreCase(m.getRole()) || "ORGANIZER".equalsIgnoreCase(m.getRole()))
                    .orElse(false);
        }
        return false;
    }

    public boolean canManageEvent(Long eventId, Long userId, String role) {
        if (userId == null || eventId == null) return false;
        return canManageEvent(new UserPrincipal(userId, null, role), eventId);
    }

    public Event requireManagedEvent(java.util.UUID publicId, Long userId, String role) {
        Event e = events.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!canManageEvent(e.getId(), userId, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not assigned to this event");
        }
        return e;
    }

    public boolean isManagerAssigned(Long eventId, Long userId) {
        return eventId != null && userId != null && managerAssignments.existsByEventIdAndUserId(eventId, userId);
    }

    public List<Long> assignedManagerEventIds(Long userId) {
        if (userId == null) return List.of();
        return managerAssignments.findByUserIdOrderByEventIdDesc(userId).stream()
                .map(x -> x.getEventId())
                .toList();
    }
}
