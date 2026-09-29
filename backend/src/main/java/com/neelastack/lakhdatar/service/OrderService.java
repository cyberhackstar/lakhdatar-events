package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final EventRepository events; private final TicketTypeRepository ticketTypes; private final OrderRepository orders; private final OrderItemRepository items;
    private final PaymentRepository payments; private final TicketRepository tickets; private final TicketReservationRepository reservations; private final TicketReservationService reservationService;
    private final RazorpayService razorpay; private final QrCredentialService qr; private final AccessTokenService accessTokens; private final AuditService audit; private final AppProperties props;
    private final JdbcTemplate jdbc; private final RateLimitService rateLimits; private final TransactionTemplate tx; private final DistributedLockService locks; private final RefundService refundService;

    public record CheckoutItem(UUID ticketTypeId,int quantity){}
    public record CheckoutRequest(UUID eventId,String customerName,String customerEmail,String customerPhone,String idempotencyKey,List<CheckoutItem> items,String clientKey){}
    public record CheckoutResponse(String orderPublicId,String orderNumber,String razorpayOrderId,String razorpayKeyId,long amountMinorUnits,String currency,Instant reservationExpiresAt){}
    public record VerifyRequest(String razorpayOrderId,String razorpayPaymentId,String razorpaySignature){}
    public record TicketRef(UUID ticketId,String ticketNumber,String accessToken){}
    public record VerifyResponse(String orderPublicId,String orderNumber,String status,List<TicketRef> tickets){}
    public record CaptureReconciliation(String orderNumber,String status){}

    public CheckoutResponse checkout(CheckoutRequest request){
        java.time.Duration checkoutWindow=java.time.Duration.ofSeconds(props.rateLimit().windowSeconds());
        String client = request.clientKey()==null?"unknown":request.clientKey();
        String checkoutEmailKey = request.customerEmail()==null?"unknown":hashForRateLimit(request.customerEmail().trim().toLowerCase());
        if(!rateLimits.allow("checkout:"+client,props.rateLimit().publicCheckoutPerWindow(),checkoutWindow)
                || !rateLimits.allow("checkout-email:"+checkoutEmailKey,Math.max(5,props.rateLimit().publicCheckoutPerWindow()/2),checkoutWindow))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many checkout attempts");
        validateText(request.customerName(),"Customer name"); validateEmail(request.customerEmail()); validatePhone(request.customerPhone());
        if(request.idempotencyKey()==null||!request.idempotencyKey().matches("[A-Za-z0-9_-]{16,100}")) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Invalid idempotency key");

        LocalCheckout local = tx.execute(status -> createLocalCheckout(request));
        if (local == null) throw new ApiException(HttpStatus.CONFLICT,"CHECKOUT_INITIALIZATION_FAILED","Checkout could not be initialized");
        return provisionRazorpayOrder(local.orderId());
    }

    private LocalCheckout createLocalCheckout(CheckoutRequest request){
        lockIdempotency(request.idempotencyKey());
        Optional<Order> existing=orders.findByIdempotencyKey(request.idempotencyKey());
        if(existing.isPresent()) {
            Order o=existing.get();
            if(!sameCheckoutRequest(o, request))
                throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","This idempotency key is already associated with a different checkout");
            if(o.getStatus()==Enums.OrderStatus.CONFIRMED) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Order is already confirmed");
            if(o.getStatus()!=Enums.OrderStatus.AWAITING_PAYMENT && o.getStatus()!=Enums.OrderStatus.CREATED)
                throw new ApiException(HttpStatus.CONFLICT,"ORDER_CLOSED","This order can no longer be paid. Start a new checkout.");
            payments.findByOrderId(o.getId()).orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"ORDER_INCOMPLETE","Order is still initializing; retry shortly"));
            if(o.getEventId()!=null){
                Event existingEvent=events.findById(o.getEventId()).orElseThrow();
                if(existingEvent.getStatus()!=Enums.EventStatus.PUBLISHED || !existingEvent.getStartsAt().isAfter(Instant.now()))
                    throw new ApiException(HttpStatus.CONFLICT,"EVENT_CLOSED","This event is no longer open for sale");
            }
            if(!reservations.findByOrderIdOrderByIdAsc(o.getId()).stream().allMatch(r->r.getStatus()==Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(Instant.now())))
                throw new ApiException(HttpStatus.CONFLICT,"RESERVATION_EXPIRED","The ticket reservation for this order has expired. Start a new checkout.");
            return new LocalCheckout(o.getId());
        }
        Event event=events.findByPublicId(request.eventId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event not found"));
        if(event.getStatus()!=Enums.EventStatus.PUBLISHED || !event.getStartsAt().isAfter(Instant.now()))
            throw new ApiException(HttpStatus.CONFLICT,"EVENT_CLOSED","This event is not open for sale");
        if(!PublicEventService.bookingWindowOpen(event,Instant.now()))
            throw new ApiException(HttpStatus.CONFLICT,event.getBookingStartsAt()!=null&&event.getBookingStartsAt().isAfter(Instant.now())?"BOOKING_NOT_STARTED":"BOOKING_CLOSED","Ticket sales for this event are not open right now");

        Map<UUID,Integer> merged=new LinkedHashMap<>();
        for(CheckoutItem i:Optional.ofNullable(request.items()).orElse(List.of())){
            if(i.ticketTypeId()==null||i.quantity()<=0||i.quantity()>props.checkout().maxTicketsPerOrder()) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CART","Each ticket quantity is invalid");
            merged.merge(i.ticketTypeId(),i.quantity(),Integer::sum);
        }
        int totalQty=merged.values().stream().mapToInt(Integer::intValue).sum();
        if(totalQty<1||totalQty>props.checkout().maxTicketsPerOrder()) throw new ApiException(HttpStatus.BAD_REQUEST,"CART_LIMIT","Too many tickets in one order");

        List<UUID> ids=new ArrayList<>(merged.keySet()); ids.sort(Comparator.comparing(UUID::toString));
        long total=0; List<TicketType> lockedTypes=new ArrayList<>();
        for(UUID id:ids){
            TicketType tt=ticketTypes.findByPublicId(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"TICKET_TYPE_NOT_FOUND","Ticket type not found"));
            if(!tt.getEventId().equals(event.getId())) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CART","Ticket type does not belong to this event");
            validateTicketType(tt,merged.get(id));
            TicketType locked=reservationService.reserve(tt.getId(),merged.get(id)); lockedTypes.add(locked);
            total=Math.addExact(total,Math.multiplyExact(locked.getPriceMinorUnits(),merged.get(id)));
        }
        if(total<=0) throw new ApiException(HttpStatus.CONFLICT,"FREE_CHECKOUT_UNSUPPORTED","Zero-value checkout is not enabled for this deployment");
        Instant expires=Instant.now().plus(props.reservation().hold());
        Order order=new Order(); order.setOrderNumber(orderNumber()); order.setEventId(event.getId()); order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail().trim().toLowerCase()); order.setCustomerPhone(normalizePhone(request.customerPhone())); order.setTotalMinorUnits(total); order.setCurrency(event.getCurrency());
        order.setStatus(Enums.OrderStatus.AWAITING_PAYMENT); order.setIdempotencyKey(request.idempotencyKey()); order=orders.saveAndFlush(order);
        for(TicketType tt:lockedTypes){
            int q=merged.get(tt.getPublicId());
            OrderItem oi=new OrderItem(); oi.setOrderId(order.getId()); oi.setTicketTypeId(tt.getId()); oi.setQuantity(q); oi.setUnitPriceMinor(tt.getPriceMinorUnits()); oi.setSubtotalMinor(Math.multiplyExact(tt.getPriceMinorUnits(),q)); items.save(oi);
            TicketReservation r=new TicketReservation(); r.setOrderId(order.getId()); r.setTicketTypeId(tt.getId()); r.setQuantity(q); r.setStatus(Enums.ReservationStatus.HELD); r.setExpiresAt(expires); reservations.save(r);
        }
        Payment p=new Payment(); p.setOrderId(order.getId()); p.setAmountMinor(total); p.setCurrency(order.getCurrency()); p.setStatus(Enums.PaymentStatus.CREATED); p.setRazorpayOrderState("NOT_CREATED"); p.setRazorpayOrderAttempts(0); payments.saveAndFlush(p);
        audit.log(null,"ORDER_CREATED","ORDER",order.getPublicId().toString(),null);
        return new LocalCheckout(order.getId());
    }

    public CheckoutResponse provisionRazorpayOrder(Long orderId){
        var lock = locks.tryAcquire("razorpay-order:" + orderId, java.time.Duration.ofSeconds(90));
        if(!lock.acquired()) throw new ApiException(HttpStatus.CONFLICT,"CHECKOUT_BUSY","Checkout is being initialized; please retry");
        try {
            ProviderOrderContext context = tx.execute(status -> {
                Order o=orders.findByIdForUpdate(orderId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
                Payment p=payments.findByOrderId(o.getId()).orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"PAYMENT_MISSING","Payment record missing"));
                if(o.getStatus()==Enums.OrderStatus.CONFIRMED) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Order is already confirmed");
                Event event = events.findById(o.getEventId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event not found"));
                Instant now = Instant.now();
                List<TicketReservation> heldReservations = reservations.findByOrderIdOrderByIdAsc(o.getId());
                boolean reservationLive = !heldReservations.isEmpty()
                        && heldReservations.stream().allMatch(r -> r.getStatus() == Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(now));
                boolean eventOpen = event.getStatus() == Enums.EventStatus.PUBLISHED && event.getStartsAt().isAfter(now);
                if(!reservationLive || !eventOpen){
                    reservationService.releaseOrder(o.getId());
                    o.setStatus(eventOpen ? Enums.OrderStatus.EXPIRED : Enums.OrderStatus.CANCELLED);
                    p.setProviderLastError(eventOpen ? "Reservation expired before payment order creation" : "Event is no longer open for sale");
                    p.setRazorpayOrderState(p.getRazorpayOrderId()==null ? "NOT_CREATED" : p.getRazorpayOrderState());
                    payments.save(p);
                    return new ProviderOrderContext(o.getId(),o.getPublicId(),o.getOrderNumber(),p.getRazorpayOrderId(),p.getAmountMinor(),p.getCurrency(),minReservationExpiry(o.getId()),false, eventOpen ? "RESERVATION_EXPIRED" : "EVENT_CLOSED", p.getRazorpayOrderState());
                }
                if(p.getRazorpayOrderId()!=null) return new ProviderOrderContext(o.getId(),o.getPublicId(),o.getOrderNumber(),p.getRazorpayOrderId(),p.getAmountMinor(),p.getCurrency(),minReservationExpiry(o.getId()),true,null, p.getRazorpayOrderState());
                p.setRazorpayOrderState("CREATING"); p.setRazorpayOrderAttempts(p.getRazorpayOrderAttempts()+1); payments.save(p);
                return new ProviderOrderContext(o.getId(),o.getPublicId(),o.getOrderNumber(),null,p.getAmountMinor(),p.getCurrency(),minReservationExpiry(o.getId()),true,null,p.getRazorpayOrderState());
            });
            if(context == null) throw new ApiException(HttpStatus.CONFLICT,"CHECKOUT_INITIALIZATION_FAILED","Checkout could not be initialized");
            if(!context.payable()){
                throw new ApiException(HttpStatus.CONFLICT,context.blockCode(),"The checkout can no longer accept payment. Please start a new checkout.");
            }
            if(context.razorpayOrderId()!=null){
                // Never present a provider-paid order as payable. This also recovers a payment if the
                // browser/webhook was interrupted after capture but before local fulfillment.
                var providerOrder=razorpay.fetchOrder(context.razorpayOrderId());
                if(providerOrder.amount()!=context.amountMinor() || !context.currency().equalsIgnoreCase(providerOrder.currency()) || !context.orderNumber().equals(providerOrder.receipt()))
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_MISMATCH","The provider order does not match this checkout");
                if("paid".equalsIgnoreCase(providerOrder.status())){
                    var providerPayment=razorpay.fetchPaymentsForOrder(providerOrder.id()).stream()
                            .filter(x -> x.amount()==context.amountMinor() && context.currency().equalsIgnoreCase(x.currency()))
                            .filter(x -> "captured".equalsIgnoreCase(x.status()) || "refunded".equalsIgnoreCase(x.status()))
                            .findFirst();
                    if(providerPayment.isPresent()){
                        var pp=providerPayment.get();
                        if("refunded".equalsIgnoreCase(pp.status())) markProviderRefunded(context.orderId(),pp);
                        else {
                            var reconciled=reconcileCapturedPayment(findPaymentId(context.orderId()),pp);
                            if("FULFILLED".equals(reconciled.status())) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Payment was already completed; retrieve the ticket from order recovery");
                            if("REFUND_PENDING".equals(reconciled.status())) completeQueuedRefundIfNeeded(findPaymentId(context.orderId()),"Provider payment captured after checkout recovery");
                        }
                    }
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_RECOVERY_PENDING","This payment session is already marked paid at the provider. Retrieve the ticket from order recovery.");
                }
                return responseForCheckout(context);
            }
            Optional<RazorpayService.ProviderOrder> recovered;
            try { recovered = razorpay.findOrderByReceipt(context.orderNumber()); }
            catch (RuntimeException ex) {
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setRazorpayOrderState("RECOVERY_PENDING");p.setProviderLastError(ex.getClass().getSimpleName());payments.save(p);}); });
                throw ex;
            }
            if(recovered.isPresent()) {
                var providerOrder=recovered.get();
                if(providerOrder.amount()!=context.amountMinor() || !context.currency().equalsIgnoreCase(providerOrder.currency()) || !context.orderNumber().equals(providerOrder.receipt()))
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_MISMATCH","A provider order with this receipt does not match this checkout");
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setRazorpayOrderId(providerOrder.id());p.setRazorpayOrderState("READY");p.setStatus(Enums.PaymentStatus.PENDING);p.setProviderLastError(null);payments.save(p);}); });
                if("paid".equalsIgnoreCase(providerOrder.status())) {
                    var recoveredPayment=razorpay.fetchPaymentsForOrder(providerOrder.id()).stream()
                            .filter(x -> x.amount()==context.amountMinor() && context.currency().equalsIgnoreCase(x.currency()))
                            .filter(x -> "captured".equalsIgnoreCase(x.status()) || "refunded".equalsIgnoreCase(x.status()))
                            .findFirst();
                    if(recoveredPayment.isPresent()) {
                        var rpPayment=recoveredPayment.get();
                        Long localPaymentId=payments.findByOrderId(context.orderId()).orElseThrow().getId();
                        if("refunded".equalsIgnoreCase(rpPayment.status())) markProviderRefunded(localPaymentId, rpPayment);
                        else {
                            var reconciled=reconcileCapturedPayment(localPaymentId, rpPayment);
                            if("FULFILLED".equals(reconciled.status())) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Payment was already completed; retrieve the ticket from order recovery");
                        }
                    }
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_RECOVERY_PENDING","This payment session is already marked paid at the provider. Please use order recovery while we finish reconciliation.");
                }
                return new CheckoutResponse(context.publicId().toString(),context.orderNumber(),providerOrder.id(),props.razorpay().keyId(),context.amountMinor(),context.currency(),context.reservationExpiresAt());
            }
            if(!"NOT_CREATED".equalsIgnoreCase(context.providerOrderState())) {
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{
                    p.setProviderLastError("Provider order creation outcome is uncertain; no duplicate provider order will be created automatically");
                    payments.save(p);
                }); });
                throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_UNCERTAIN","Payment gateway order creation is awaiting safe reconciliation. Please retry later or start a new checkout.");
            }
            RazorpayService.RazorpayOrderRef rp;
            try { rp=razorpay.createOrder(context.amountMinor(),context.currency(),context.orderNumber()); }
            catch (RuntimeException ex) {
                // A timeout may mean Razorpay created the order even though the response was lost. Re-check by receipt before retrying.
                try {
                    var retryLookup=razorpay.findOrderByReceipt(context.orderNumber());
                    if(retryLookup.isPresent()) {
                        var providerOrder=retryLookup.get();
                        if(providerOrder.amount()==context.amountMinor() && context.currency().equalsIgnoreCase(providerOrder.currency())) {
                            tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setRazorpayOrderId(providerOrder.id());p.setRazorpayOrderState("READY");p.setStatus(Enums.PaymentStatus.PENDING);p.setProviderLastError(null);payments.save(p);}); });
                            return new CheckoutResponse(context.publicId().toString(),context.orderNumber(),providerOrder.id(),props.razorpay().keyId(),context.amountMinor(),context.currency(),context.reservationExpiresAt());
                        }
                    }
                } catch (RuntimeException ignored) { }
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setRazorpayOrderState("RECOVERY_PENDING");p.setProviderLastError(ex.getClass().getSimpleName());payments.save(p);}); });
                throw ex;
            }
            tx.executeWithoutResult(s->{
                Payment p=payments.findByOrderId(context.orderId()).orElseThrow();
                if(p.getRazorpayOrderId()==null){p.setRazorpayOrderId(rp.razorpayOrderId());p.setRazorpayOrderState("READY");p.setStatus(Enums.PaymentStatus.PENDING);p.setProviderLastError(null);payments.save(p);}
            });
            return new CheckoutResponse(context.publicId().toString(),context.orderNumber(),rp.razorpayOrderId(),rp.keyId(),context.amountMinor(),context.currency(),context.reservationExpiresAt());
        } finally { lock.close(); }
    }

    public VerifyResponse verifyAndConfirm(VerifyRequest req){
        if(!rateLimits.allow("verify:"+(req.razorpayOrderId()==null?"unknown":req.razorpayOrderId()),20,java.time.Duration.ofMinutes(1)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many payment verification attempts");
        Payment p=payments.findByRazorpayOrderId(req.razorpayOrderId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment session not found"));
        if(!razorpay.verifyPaymentSignature(p.getRazorpayOrderId(),req.razorpayPaymentId(),req.razorpaySignature()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAYMENT_SIGNATURE","Payment verification failed");
        RazorpayService.ProviderPayment provider=razorpay.fetchPayment(req.razorpayPaymentId());
        tx.executeWithoutResult(status->{
            Payment locked=payments.findByIdForUpdate(p.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment session not found"));
            if(locked.getStatus()==Enums.PaymentStatus.REFUNDED || locked.getStatus()==Enums.PaymentStatus.REFUND_PENDING)
                throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_NOT_REUSABLE","This payment is already closed for ticket issuance");
            validateProviderPayment(locked,provider);
            if(locked.getRazorpayPaymentId()!=null && !Objects.equals(locked.getRazorpayPaymentId(),provider.id()))
                throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_ID_MISMATCH","A different payment is already associated with this order");
            locked.setRazorpayPaymentId(provider.id());
            locked.setRazorpaySignature(req.razorpaySignature());
            locked.setStatus(Enums.PaymentStatus.CAPTURED);
            payments.save(locked);
        });
        CaptureReconciliation result=reconcileCapturedPayment(p.getId(),provider);
        if ("REFUND_PENDING".equals(result.status())) {
            Order pendingOrder=orders.findByOrderNumber(result.orderNumber()).orElseThrow();
            return new VerifyResponse(pendingOrder.getPublicId().toString(), result.orderNumber(), "REFUND_PENDING", List.of());
        }
        Order o=orders.findByOrderNumber(result.orderNumber()).orElseThrow(); return response(o);
    }

    public CaptureReconciliation reconcileCapturedPayment(Long paymentId, RazorpayService.ProviderPayment provider){
        return tx.execute(status -> {
            Payment p=payments.findByIdForUpdate(paymentId).orElseThrow();
            if(p.getStatus()==Enums.PaymentStatus.REFUNDED) return new CaptureReconciliation(findOrderNumber(p.getOrderId()),"REFUNDED");
            validateProviderPayment(p, provider);
            Order o=orders.findByIdForUpdate(p.getOrderId()).orElseThrow();
            Event event=events.findById(o.getEventId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,"EVENT_NOT_FOUND","Event no longer exists"));
            if(p.getRazorpayPaymentId()!=null && !Objects.equals(p.getRazorpayPaymentId(), provider.id()))
                throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_ID_MISMATCH","A different payment is already associated with this order");
            if(o.getStatus()==Enums.OrderStatus.CONFIRMED){p.setStatus(Enums.PaymentStatus.COMPLETED);return new CaptureReconciliation(o.getOrderNumber(),"FULFILLED");}
            p.setRazorpayPaymentId(provider.id());
            p.setStatus(Enums.PaymentStatus.CAPTURED); payments.save(p);
            List<TicketReservation> rs=reservations.findByOrderIdForUpdate(o.getId());
            Instant now=Instant.now();
            boolean reservationValid=!rs.isEmpty() && rs.stream().allMatch(r->r.getStatus()==Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(now));
            boolean eventOpen=event.getStatus()==Enums.EventStatus.PUBLISHED && event.getStartsAt().isAfter(now);
            boolean orderPayable=o.getStatus()==Enums.OrderStatus.CREATED || o.getStatus()==Enums.OrderStatus.AWAITING_PAYMENT;
            if(!reservationValid || !eventOpen || !orderPayable){
                p.setStatus(Enums.PaymentStatus.REFUND_PENDING); payments.save(p);
                reservationService.releaseOrder(o.getId());
                if(o.getStatus()!=Enums.OrderStatus.CONFIRMED) o.setStatus(Enums.OrderStatus.CANCELLED);
                // Queue compensation in the same transaction so a captured payment can never be silently orphaned.
                return new CaptureReconciliation(o.getOrderNumber(),"REFUND_PENDING");
            }
            fulfillOrderLocked(o,p,rs);
            return new CaptureReconciliation(o.getOrderNumber(),"FULFILLED");
        });
    }

    public void completeQueuedRefundIfNeeded(Long paymentId,String reason){ refundService.queueCapturedPaymentRefund(paymentId,reason); }

    public boolean markProviderRefunded(Long paymentId, RazorpayService.ProviderPayment provider){
        boolean partial = Boolean.TRUE.equals(tx.execute(status -> {
            Payment p=payments.findByIdForUpdate(paymentId).orElseThrow();
            if(p.getStatus()==Enums.PaymentStatus.REFUNDED) return false;
            if(!Objects.equals(p.getRazorpayOrderId(), provider.orderId()) || provider.amount()!=p.getAmountMinor() || !p.getCurrency().equalsIgnoreCase(provider.currency()))
                throw new ApiException(HttpStatus.CONFLICT,"PROVIDER_PAYMENT_MISMATCH","Provider refund does not match local payment");
            if(p.getRazorpayPaymentId()!=null && !Objects.equals(p.getRazorpayPaymentId(), provider.id()))
                throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_ID_MISMATCH","Provider refund refers to a different payment");
            p.setRazorpayPaymentId(provider.id());
            if(provider.amountRefunded() < p.getAmountMinor()) {
                p.setStatus(Enums.PaymentStatus.REFUND_PENDING);
                payments.save(p);
                return true;
            }
            p.setStatus(Enums.PaymentStatus.REFUNDED);
            Order o=orders.findById(p.getOrderId()).orElseThrow();
            if(o.getStatus()!=Enums.OrderStatus.CONFIRMED) o.setStatus(Enums.OrderStatus.CANCELLED);
            reservationService.releaseOrder(o.getId());
            tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).forEach(t->{if(t.getStatus()!=Enums.TicketStatus.CHECKED_IN)t.setStatus(Enums.TicketStatus.REFUNDED);});
            audit.log(null,"PROVIDER_REFUND_RECONCILED","PAYMENT",p.getPublicId().toString(),null);
            return false;
        }));
        if(partial) completeQueuedRefundIfNeeded(paymentId,"Completing partial provider refund");
        return partial;
    }

    private void fulfillOrderLocked(Order o, Payment p, List<TicketReservation> rs){
        List<Ticket> existing=tickets.findByOrderIdOrderByTicketNumberAsc(o.getId());
        if(!existing.isEmpty()){o.setStatus(Enums.OrderStatus.CONFIRMED);p.setStatus(Enums.PaymentStatus.COMPLETED);return;}
        for(TicketReservation r:rs){ if(r.getStatus()!=Enums.ReservationStatus.HELD || !r.getExpiresAt().isAfter(Instant.now())) throw new ApiException(HttpStatus.CONFLICT,"RESERVATION_EXPIRED","Ticket reservation expired"); reservationService.confirmSale(r.getTicketTypeId(),r.getQuantity()); r.setStatus(Enums.ReservationStatus.CONFIRMED); }
        List<OrderItem> orderItems=items.findByOrderId(o.getId());
        for(OrderItem oi:orderItems){
            for(int i=0;i<oi.getQuantity();i++){
                Ticket t=new Ticket();t.setOrderId(o.getId());t.setOrderItemId(oi.getId());t.setEventId(o.getEventId());t.setTicketTypeId(oi.getTicketTypeId());t.setAttendeeName(o.getCustomerName());t.setTicketNumber(ticketNumber());t.setStatus(Enums.TicketStatus.ISSUED);String credential=qr.credentialFor(t.getPublicId());t.setQrCredentialHash(qr.hash(credential));tickets.save(t);
            }
        }
        o.setStatus(Enums.OrderStatus.CONFIRMED);p.setStatus(Enums.PaymentStatus.COMPLETED);audit.log(o.getUserId(),"TICKETS_ISSUED","ORDER",o.getPublicId().toString(),null);
    }

    public void failPayment(String razorpayOrderId,String reason){
        tx.executeWithoutResult(status->{
            payments.findByRazorpayOrderId(razorpayOrderId).ifPresent(found->{
                Payment p=payments.findByIdForUpdate(found.getId()).orElseThrow();
                if(p.getStatus()==Enums.PaymentStatus.COMPLETED||p.getStatus()==Enums.PaymentStatus.CAPTURED||p.getStatus()==Enums.PaymentStatus.REFUND_PENDING||p.getStatus()==Enums.PaymentStatus.REFUNDED)return;
                // A failed payment attempt does not prove that the Razorpay order can no longer succeed.
                // Keep the order payable while recording the latest attempt failure; reconciliation will
                // determine the authoritative provider state later.
                p.setStatus(Enums.PaymentStatus.PENDING);
                p.setFailureReason(reason);
                p.setRazorpayOrderState("READY");
                payments.save(p);
            });
        });
    }

    public VerifyResponse response(Order o){List<TicketRef> refs=tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream().map(t->new TicketRef(t.getPublicId(),t.getTicketNumber(),accessTokens.issue(t.getPublicId()))).toList();return new VerifyResponse(o.getPublicId().toString(),o.getOrderNumber(),o.getStatus().name(),refs);}
    public CheckoutResponse existingResponse(Order o){return provisionRazorpayOrder(o.getId());}

    /**
     * Reconciles local payments for which provider-order creation did not complete.
     * Kept on the primary order service to avoid an extra Spring component/classpath
     * boundary that caused stale-class failures on Windows/OneDrive workspaces.
     */
    @Scheduled(fixedDelayString = "${app.razorpay.order-recovery-sweep:30000}")
    public void recoverMissingProviderOrders(){
        var candidates = payments.findTop100ByRazorpayOrderIdIsNullAndStatusInOrderByCreatedAtAsc(
                List.of(Enums.PaymentStatus.CREATED, Enums.PaymentStatus.PENDING,
                        Enums.PaymentStatus.PAYMENT_INITIATED, Enums.PaymentStatus.AUTHORIZED));
        for (var payment : candidates) {
            try {
                provisionRazorpayOrder(payment.getOrderId());
            } catch (Exception ex) {
                // Recovery is best-effort; the next scheduled sweep retries the payment.
            }
        }
    }
    public void validateProviderPayment(Payment p,RazorpayService.ProviderPayment provider){
        if(provider.id()==null||!Objects.equals(p.getRazorpayOrderId(),provider.orderId())) throw new ApiException(HttpStatus.BAD_REQUEST,"PAYMENT_MISMATCH","Payment does not belong to this order");
        if(provider.amount()!=p.getAmountMinor()||!p.getCurrency().equalsIgnoreCase(provider.currency())) throw new ApiException(HttpStatus.BAD_REQUEST,"PAYMENT_AMOUNT_MISMATCH","Payment amount could not be verified");
        if(!"captured".equalsIgnoreCase(provider.status()) || !provider.captured()) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_NOT_CAPTURED","Payment has not been captured yet");
        if(provider.amountRefunded()>0) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PARTIALLY_REFUNDED","Payment has already been refunded and cannot be issued as a new ticket purchase");
    }
    public record PublicEventControllerTicket(UUID ticketId,String ticketNumber,String accessToken){}
    public record RecoveryOrder(String orderNumber,Enums.OrderStatus status,String eventName,List<PublicEventControllerTicket> tickets){}
    public RecoveryOrder findForRecovery(String orderNumber,String email){Order o=orders.findByOrderNumber(orderNumber.trim().toUpperCase()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));if(!o.getCustomerEmail().equalsIgnoreCase(email.trim()))throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found");Event e=events.findById(o.getEventId()).orElseThrow();List<PublicEventControllerTicket> ts=tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream().map(t->new PublicEventControllerTicket(t.getPublicId(),t.getTicketNumber(),accessTokens.issue(t.getPublicId()))).toList();return new RecoveryOrder(o.getOrderNumber(),o.getStatus(),e.getName(),ts);}
    private boolean sameCheckoutRequest(Order o, CheckoutRequest request){
        if(!Objects.equals(o.getCustomerName(),request.customerName().trim())) return false;
        if(!Objects.equals(o.getCustomerEmail(),request.customerEmail().trim().toLowerCase())) return false;
        if(!Objects.equals(normalizePhone(o.getCustomerPhone()),normalizePhone(request.customerPhone()))) return false;
        Event event=events.findByPublicId(request.eventId()).orElse(null);
        if(event==null || !Objects.equals(event.getId(),o.getEventId())) return false;
        Map<Long,Integer> expected=new HashMap<>();
        for(CheckoutItem item:Optional.ofNullable(request.items()).orElse(List.of())){
            if(item.ticketTypeId()==null || item.quantity()<=0) return false;
            TicketType tt=ticketTypes.findByPublicId(item.ticketTypeId()).orElse(null);
            if(tt==null || !Objects.equals(tt.getEventId(),o.getEventId())) return false;
            expected.merge(tt.getId(),item.quantity(),Integer::sum);
        }
        Map<Long,Integer> actual=new HashMap<>();
        for(OrderItem item:items.findByOrderId(o.getId())) actual.put(item.getTicketTypeId(),item.getQuantity());
        return actual.equals(expected);
    }
    private ProviderOrderContext context(Long orderId){Order o=orders.findById(orderId).orElseThrow();Payment p=payments.findByOrderId(orderId).orElseThrow();return new ProviderOrderContext(orderId,o.getPublicId(),o.getOrderNumber(),p.getRazorpayOrderId(),p.getAmountMinor(),p.getCurrency(),minReservationExpiry(orderId),true,null,p.getRazorpayOrderState());}
    private CheckoutResponse responseForCheckout(ProviderOrderContext c){return new CheckoutResponse(c.publicId().toString(),c.orderNumber(),c.razorpayOrderId(),props.razorpay().keyId(),c.amountMinor(),c.currency(),c.reservationExpiresAt());}
    private record LocalCheckout(Long orderId){}
    private record ProviderOrderContext(Long orderId,UUID publicId,String orderNumber,String razorpayOrderId,long amountMinor,String currency,Instant reservationExpiresAt,boolean payable,String blockCode,String providerOrderState){}
    private Instant minReservationExpiry(Long orderId){return reservations.findByOrderIdOrderByIdAsc(orderId).stream().map(TicketReservation::getExpiresAt).min(Instant::compareTo).orElse(Instant.now());}
    private Long findPaymentId(Long orderId){return payments.findByOrderId(orderId).map(Payment::getId).orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"PAYMENT_MISSING","Payment record missing"));}
    private String findOrderNumber(Long orderId){return orders.findById(orderId).map(Order::getOrderNumber).orElse("");}
    private void lockIdempotency(String key){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))",(rs,n)->rs.getObject(1),key);}
    private String orderNumber(){return "LK-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase();}
    private String ticketNumber(){return "LK-"+UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();}
    private String normalizePhone(String p){return p==null?null:p.trim();}
    private String hashForRateLimit(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void validateText(String v,String name){if(v==null||v.trim().length()<2||v.trim().length()>120)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",name+" is required");}
    private void validateEmail(String e){if(e==null||e.length()>255||!e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EMAIL","Enter a valid email address");}
    private void validatePhone(String p){if(p==null||p.isBlank())return; if(p.length()>40||!p.matches("^[0-9+()\\- .]{7,40}$"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PHONE","Enter a valid phone number");}
    private void validateTicketType(TicketType tt,int qty){Instant now=Instant.now();if(tt.getStatus()!=Enums.TicketTypeStatus.ACTIVE)throw new ApiException(HttpStatus.CONFLICT,"TICKET_TYPE_CLOSED","Ticket type is not on sale");if(tt.getSaleStartsAt()!=null&&now.isBefore(tt.getSaleStartsAt()))throw new ApiException(HttpStatus.CONFLICT,"SALE_NOT_STARTED","Ticket sales have not started");if(tt.getSaleEndsAt()!=null&&!now.isBefore(tt.getSaleEndsAt()))throw new ApiException(HttpStatus.CONFLICT,"SALE_ENDED","Ticket sales have ended");if(qty<tt.getMinPerOrder()||qty>tt.getMaxPerOrder())throw new ApiException(HttpStatus.BAD_REQUEST,"QUANTITY_LIMIT","Quantity for "+tt.getName()+" must be between "+tt.getMinPerOrder()+" and "+tt.getMaxPerOrder());}
}
