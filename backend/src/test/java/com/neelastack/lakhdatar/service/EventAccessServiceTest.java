package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.repository.EventManagerAssignmentRepository;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrganizerMemberRepository;
import com.neelastack.lakhdatar.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventAccessServiceTest {
    @Mock OrganizerMemberRepository members;
    @Mock EventManagerAssignmentRepository assignments;
    @Mock EventRepository events;
    @InjectMocks EventAccessService access;

    @Test void eventManagerNeedsExplicitEventAssignment() {
        Event e = new Event(); e.setId(42L); e.setOrganizerId(7L);
        when(events.findById(42L)).thenReturn(Optional.of(e));
        when(assignments.existsByEventIdAndUserId(42L, 99L)).thenReturn(false);

        assertFalse(access.canManageEvent(new UserPrincipal(99L, "manager@example.com", "EVENT_MANAGER"), 42L));
        verify(assignments).existsByEventIdAndUserId(42L, 99L);
        verifyNoInteractions(members);
    }

    @Test void eventManagerCanOnlyManageAssignedEvent() {
        Event e = new Event(); e.setId(42L); e.setOrganizerId(7L);
        when(events.findById(42L)).thenReturn(Optional.of(e));
        when(assignments.existsByEventIdAndUserId(42L, 99L)).thenReturn(true);

        assertTrue(access.canManageEvent(new UserPrincipal(99L, "manager@example.com", "EVENT_MANAGER"), 42L));
    }

    @Test void organizerMembershipDoesNotElevateEventManager() {
        Event e = new Event(); e.setId(42L); e.setOrganizerId(7L);
        when(events.findById(42L)).thenReturn(Optional.of(e));

        assertFalse(access.canManageEvent(new UserPrincipal(99L, "manager@example.com", "EVENT_MANAGER"), 42L));
        verifyNoInteractions(members);
    }

    @Test void adminRemainsPlatformWide() {
        assertTrue(access.canManageEvent(new UserPrincipal(1L, "admin@example.com", "ADMIN"), 999L));
        verifyNoInteractions(events, assignments, members);
    }
}
