package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative publication checklist. The UI may mirror these checks for UX, but this policy
 * is evaluated again immediately before the state transition in EventManagementService.
 */
public final class PublishReadiness {
    private PublishReadiness() {}

    public record Result(boolean ready, List<String> blockers, List<String> warnings) {
        public Result {
            blockers = List.copyOf(blockers == null ? List.of() : blockers);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }

    public static Result evaluate(String role,
                                  Enums.EventStatus status,
                                  Instant startsAt,
                                  Instant endsAt,
                                  Instant bookingStartsAt,
                                  Instant bookingEndsAt,
                                  boolean hasTicketTypes,
                                  boolean hasActiveTicketTypes,
                                  boolean paymentProviderConfigured) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean managementRole = "ADMIN".equals(role) || "ORGANIZER".equals(role);
        if (!managementRole) blockers.add("Only platform administrators or organizer owners can publish events.");

        if (status == Enums.EventStatus.PUBLISHED) {
            return new Result(true, blockers, List.of("Event is already published."));
        }
        if (status != Enums.EventStatus.DRAFT && status != Enums.EventStatus.UNPUBLISHED) {
            blockers.add("Only draft or unpublished events can be published.");
        }
        Instant now = Instant.now();
        if (startsAt == null) blockers.add("Event start date and time are required.");
        else if (!startsAt.isAfter(now)) blockers.add("Event start date and time must be in the future.");

        if (!hasTicketTypes) blockers.add("Add at least one ticket type before publishing.");
        else if (!hasActiveTicketTypes) blockers.add("At least one ticket type must be ACTIVE before publishing.");

        if (!paymentProviderConfigured) blockers.add("The selected payment provider is not configured.");

        Instant effectiveEnd = endsAt != null ? endsAt : startsAt;
        if (endsAt != null && startsAt != null && !endsAt.isAfter(startsAt)) blockers.add("Event end must be after event start.");
        if (bookingStartsAt != null && startsAt != null && !bookingStartsAt.isBefore(startsAt)) blockers.add("Booking start must be before the event begins.");
        if (bookingStartsAt != null && bookingEndsAt != null && !bookingEndsAt.isAfter(bookingStartsAt)) blockers.add("Booking end must be after booking start.");
        if (bookingEndsAt != null && effectiveEnd != null && bookingEndsAt.isAfter(effectiveEnd)) blockers.add("Booking can remain open only until the event ends.");

        return new Result(blockers.isEmpty(), blockers, warnings);
    }
}
