package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckInConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired CheckInService checkInService;
    @Autowired QrCredentialService qr;
    @Autowired OrganizerRepository organizers;
    @Autowired EventRepository events;
    @Autowired TicketTypeRepository ticketTypes;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository orderItems;
    @Autowired TicketRepository tickets;
    @Autowired UserRepository users;

    @Test
    void exactlyOneAcceptedUnderConcurrentScans() throws Exception {
        Organizer organizer = new Organizer(); organizer.setName("Concurrency Organizer"); organizer.setSlug("concurrency-organizer"); organizer = organizers.saveAndFlush(organizer);
        Event event = new Event(); event.setSlug("concurrency-event"); event.setOrganizerId(organizer.getId()); event.setName("Concurrency Event"); event.setStartsAt(Instant.now().minusSeconds(60)); event.setStatus(Enums.EventStatus.PUBLISHED); event = events.saveAndFlush(event);
        TicketType type = new TicketType(); type.setEventId(event.getId()); type.setName("General"); type.setPriceMinorUnits(10000L); type.setCurrency("INR"); type.setTotalQuantity(1); type.setReservedQuantity(0); type.setSoldQuantity(1); type.setMinPerOrder(1); type.setMaxPerOrder(1); type.setStatus(Enums.TicketTypeStatus.ACTIVE); type = ticketTypes.saveAndFlush(type);
        Order order = new Order(); order.setOrderNumber("LK-CONCURRENCY01"); order.setEventId(event.getId()); order.setCustomerName("Test User"); order.setCustomerEmail("test@example.com"); order.setTotalMinorUnits(10000L); order.setCurrency("INR"); order.setStatus(Enums.OrderStatus.CONFIRMED); order.setIdempotencyKey("concurrency-idempotency-001"); order = orders.saveAndFlush(order);
        OrderItem item = new OrderItem(); item.setOrderId(order.getId()); item.setTicketTypeId(type.getId()); item.setQuantity(1); item.setUnitPriceMinor(10000L); item.setSubtotalMinor(10000L); item = orderItems.saveAndFlush(item);
        User staffUserEntity = new User(); staffUserEntity.setEmail("scanner-concurrency@example.com"); staffUserEntity.setFullName("Concurrency Scanner"); staffUserEntity.setPasswordHash("$2a$10$placeholderhashplaceholderhashplaceholderhashplaceholderhash"); staffUserEntity.setRole(Enums.UserRole.ADMIN); staffUserEntity.setEnabled(true); final Long staffUserId = users.saveAndFlush(staffUserEntity).getId();
        Ticket ticket = new Ticket(); ticket.setOrderId(order.getId()); ticket.setOrderItemId(item.getId()); ticket.setEventId(event.getId()); ticket.setTicketTypeId(type.getId()); ticket.setTicketNumber("LK-TEST0001"); ticket.setStatus(Enums.TicketStatus.ISSUED); String credential = qr.credentialFor(ticket.getPublicId()); ticket.setQrCredentialHash(qr.hash(credential)); tickets.saveAndFlush(ticket);

        int concurrency=100; ExecutorService pool=Executors.newFixedThreadPool(20); CountDownLatch gate=new CountDownLatch(1); List<Future<Enums.CheckInResult>> futures=new ArrayList<>(); UUID eventId=event.getPublicId();
        for(int i=0;i<concurrency;i++) futures.add(pool.submit(() -> { gate.await(); return checkInService.scan(new CheckInService.ScanRequest(credential,eventId,staffUserId,"ADMIN","Gate A","test"+Thread.currentThread().getId())).result(); }));
        gate.countDown(); AtomicInteger accepted=new AtomicInteger(), alreadyUsed=new AtomicInteger();
        for(Future<Enums.CheckInResult> f:futures){ Enums.CheckInResult r=f.get(30,TimeUnit.SECONDS); if(r==Enums.CheckInResult.ACCEPTED)accepted.incrementAndGet(); if(r==Enums.CheckInResult.ALREADY_USED)alreadyUsed.incrementAndGet(); }
        pool.shutdownNow(); assertEquals(1,accepted.get()); assertEquals(concurrency-1,alreadyUsed.get());
    }
}
