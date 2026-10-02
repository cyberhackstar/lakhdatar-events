package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.EventStaff;
import com.neelastack.lakhdatar.repository.EventStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffAccessService {
    private final EventStaffRepository staff;
    private final EventAccessService eventAccess;

    public boolean canAccessEventAndGate(Event event, Long userId, String role, String requestedGate) {
        if (event == null || userId == null) return false;
        if ("ADMIN".equals(role)) return true;

        // Event managers are event-scoped. Organizer membership by itself does not grant scanner access.
        if ("EVENT_MANAGER".equals(role)) {
            return eventAccess.isManagerAssigned(event.getId(), userId);
        }

        // Organizer owners may operate all events belonging to their organizer.
        if ("ORGANIZER".equals(role)) {
            return eventAccess.canManageEvent(event.getId(), userId, role);
        }

        EventStaff assignment = staff.findByEventIdAndUserId(event.getId(), userId).orElse(null);
        if (assignment == null) return false;
        String assigned = assignment.getGate();
        String requested = requestedGate == null ? null : requestedGate.trim();
        // Staff permissions are gate-bound. A missing gate must never broaden access.
        return assigned != null && !assigned.isBlank() && requested != null && !requested.isBlank()
                && assigned.equalsIgnoreCase(requested);
    }
}
