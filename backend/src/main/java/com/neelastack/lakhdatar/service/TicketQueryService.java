package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketQueryService {
    private final TicketRepository tickets;
    private final TicketTypeRepository ticketTypes;
    private final OrderItemRepository orderItems;
    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final VenueRepository venues;
    private final UserRepository users;
    private final AccessTokenService accessTokens;
    private final QrCredentialService qr;
    private final BrandService brand;

    public record TicketView(UUID ticketId, String ticketNumber, String status, String attendeeName, String eventName,
                             java.time.Instant startsAt, java.time.Instant endsAt, String venueName, String venueAddress,
                             String ticketType, long amountMinorUnits, String currency, java.time.Instant checkedInAt,
                             String source, String issuedByName, BrandService.BrandView brand) {}

    public TicketView get(UUID id, String token) {
        if (!accessTokens.verify(token, id))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TICKET_LINK", "Ticket link is invalid or expired");
        Ticket t = tickets.findByPublicId(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found"));
        Event e = events.findById(t.getEventId()).orElseThrow();
        TicketType tt = ticketTypes.findById(t.getTicketTypeId()).orElseThrow();
        long amount = orderItems.findByOrderIdAndTicketTypeId(t.getOrderId(), t.getTicketTypeId())
                .map(oi -> oi.getUnitPriceMinor())
                .orElse(tt.getPriceMinorUnits());
        Organizer o = organizers.findById(e.getOrganizerId()).orElse(null);
        Venue v = e.getVenueId() == null ? null : venues.findById(e.getVenueId()).orElse(null);
        String address = v == null ? null : ((v.getAddress() == null ? "" : v.getAddress() + ", ") + (v.getCity() == null ? "" : v.getCity()));

        if (t.getStatus()==Enums.TicketStatus.CANCELLED || t.getStatus()==Enums.TicketStatus.REFUNDED) {
            throw new ApiException(HttpStatus.GONE,"TICKET_CLOSED","This ticket is no longer active");
        }
        String displayStatus = t.getStatus().name();
        if (e.getStatus() == Enums.EventStatus.CANCELLED && t.getStatus() != Enums.TicketStatus.REFUNDED) {
            // Never tell a customer that an event-cancelled ticket is still valid.
            displayStatus = Enums.TicketStatus.CANCELLED.name();
        }
        String issuerName = t.getIssuedByUserId() == null ? null : users.findById(t.getIssuedByUserId()).map(User::getFullName).orElse(null);
        String cred = qr.credentialFor(t.getPublicId());
        return new TicketView(t.getPublicId(), t.getTicketNumber(), displayStatus, t.getAttendeeName(), e.getName(),
                e.getStartsAt(), e.getEndsAt(), v == null ? null : v.getName(), address, tt.getName(), amount,
                e.getCurrency(), t.getCheckedInAt(), t.getSource().name(), issuerName,
                brand.view(e.getBrandConfigId(), o == null ? "Event organizer" : o.getName()));
    }
}
