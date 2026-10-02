package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.EventStaff;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.EventStaffRepository;
import com.neelastack.lakhdatar.repository.OrganizerMemberRepository;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {
    private final EventStaffRepository staff;
    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final OrganizerMemberRepository members;

    public record StaffEvent(UUID id, String name, String slug, String gate, String status, Instant startsAt, String organizerName) {}

    @GetMapping("/events")
    public List<StaffEvent> events(Authentication a) {
        UserPrincipal p = (UserPrincipal) a.getPrincipal();
        List<StaffEvent> out = new ArrayList<>();
        if ("ADMIN".equals(p.role())) {
            addEvents(out, events.findAllByOrderByStartsAtDesc(), null);
            return out;
        }
        if ("EVENT_MANAGER".equals(p.role())) {
            addEvents(out, events.findAllByManagerUserIdOrderByStartsAtDesc(p.userId()), "All gates");
            return out;
        }
        if ("ORGANIZER".equals(p.role())) {
            List<Long> organizerIds=members.findByUserId(p.userId()).stream()
                    .filter(m -> "OWNER".equalsIgnoreCase(m.getRole()) || "ORGANIZER".equalsIgnoreCase(m.getRole()))
                    .map(com.neelastack.lakhdatar.domain.OrganizerMember::getOrganizerId).toList();
            addEvents(out, events.findByOrganizerIdInOrderByStartsAtDesc(organizerIds), "All gates");
            return out;
        }
        List<EventStaff> assignments=staff.findByUserIdOrderByEventIdDesc(p.userId());
        Map<Long,Event> eventsById=events.findAllById(assignments.stream().map(EventStaff::getEventId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Event::getId,java.util.function.Function.identity()));
        Set<Long> organizerIds=eventsById.values().stream().map(Event::getOrganizerId).collect(java.util.stream.Collectors.toSet());
        Map<Long,com.neelastack.lakhdatar.domain.Organizer> organizersById=organizers.findAllById(organizerIds).stream()
                .collect(java.util.stream.Collectors.toMap(com.neelastack.lakhdatar.domain.Organizer::getId,java.util.function.Function.identity()));
        for (EventStaff assignment : assignments) {
            Event e=eventsById.get(assignment.getEventId());
            if (e != null) add(out, e, assignment.getGate(), organizersById.get(e.getOrganizerId()));
        }
        return out;
    }

    private void addEvents(List<StaffEvent> out, List<Event> list, String gate) {
        for (Event e : list) add(out, e, gate);
    }

    private void add(List<StaffEvent> out, Event e, String gate) {
        var o = organizers.findById(e.getOrganizerId()).orElse(null);
        add(out,e,gate,o);
    }

    private void add(List<StaffEvent> out, Event e, String gate, com.neelastack.lakhdatar.domain.Organizer o) {
        out.add(new StaffEvent(e.getPublicId(), e.getName(), e.getSlug(),
                gate == null ? "All gates" : gate,
                e.getStatus().name(), e.getStartsAt(), o == null ? "" : o.getName()));
    }
}
