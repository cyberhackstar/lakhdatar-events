package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final EventRepository events; private final TicketTypeRepository ticketTypes; private final OrderRepository orders; private final OrderItemRepository items;
    private final PaymentRepository payments; private final PaymentAttemptRepository paymentAttempts; private final TicketRepository tickets; private final TicketReservationRepository reservations; private final TicketReservationService reservationService;
    private final TicketMailService ticketMail; private final PaymentGatewayRouter gateways; private final QrCredentialService qr; private final AccessTokenService accessTokens; private final AuditService audit; private final AppProperties props;
    private final JdbcTemplate jdbc; private final RateLimitService rateLimits; private final TransactionTemplate tx; private final DistributedLockService locks; private final RefundService refundService; private final PaymentStateMachine paymentStateMachine;
    private final SecureRandom secureRandom = new SecureRandom();
    @org.springframework.beans.factory.annotation.Value("${app.payment.reconciliation-window-hours:168}") private long recoveryWindowHours = 168;
    @org.springframework.beans.factory.annotation.Value("${app.payment.recovery-recheck-ms:30000}") private long recoveryRecheckMs = 30_000;
    @org.springframework.beans.factory.annotation.Value("${app.worker.enabled:true}") private boolean workerEnabled = true;

    public record CheckoutItem(UUID ticketTypeId,int quantity){}
    public record CheckoutRequest(UUID eventId,String customerName,String customerEmail,String customerPhone,String idempotencyKey,List<CheckoutItem> items,String clientKey){}
    public record CheckoutResponse(String orderPublicId,String orderNumber,String provider,String providerOrderId,String providerPublicKey,String providerSessionId,
            long amountMinorUnits,String currency,Instant reservationExpiresAt,String providerCheckoutMode,
            @JsonIgnore String checkoutSessionToken,long checkoutSessionTtlSeconds){}
    public record VerifyRequest(String providerOrderId,String providerPaymentId,String providerSignature,String checkoutSessionToken,String clientKey){
        public VerifyRequest(String providerOrderId,String providerPaymentId,String providerSignature,String checkoutSessionToken){
            this(providerOrderId,providerPaymentId,providerSignature,checkoutSessionToken,"unknown");
        }
    }
    public record TicketRef(UUID ticketId,String ticketNumber,String accessToken){}
    public record VerifyResponse(String orderPublicId,String orderNumber,String status,List<TicketRef> tickets){}
    public record CaptureReconciliation(String orderNumber,String status){}

    public CheckoutResponse checkout(CheckoutRequest request){
        long started = System.nanoTime();
        java.time.Duration checkoutWindow=java.time.Duration.ofSeconds(props.rateLimit().windowSeconds());
        String client = request.clientKey()==null?"unknown":request.clientKey();
        String checkoutEmailKey = request.customerEmail()==null?"unknown":hashForRateLimit(request.customerEmail().trim().toLowerCase(java.util.Locale.ROOT));
        if(!rateLimits.allow("checkout:"+client,props.rateLimit().publicCheckoutPerWindow(),checkoutWindow)
                || !rateLimits.allow("checkout-email:"+checkoutEmailKey,Math.max(5,props.rateLimit().publicCheckoutPerWindow()/2),checkoutWindow))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many checkout attempts");
        validateText(request.customerName(),"Customer name"); validateEmail(request.customerEmail()); validatePhone(request.customerPhone());
        if(request.idempotencyKey()==null||!request.idempotencyKey().matches("[A-Za-z0-9_-]{16,100}")) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Invalid idempotency key");

        LocalCheckout local = tx.execute(status -> createLocalCheckout(request));
        if (local == null) throw new ApiException(HttpStatus.CONFLICT,"CHECKOUT_INITIALIZATION_FAILED","Checkout could not be initialized");
        CheckoutResponse response = provisionPaymentOrder(local.orderId(), local.checkoutSessionToken());
        EnterpriseLog.info(log, "order.checkout.initialized", "event.category", "order", "order.id", local.orderId(), "payment.provider", response.provider(), "payment.amount_minor", response.amountMinorUnits(), "cart.item_count", request.items()==null?0:request.items().size(), "duration.ms", (System.nanoTime()-started)/1_000_000L);
        return response;
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
                Event existingEvent=events.findByIdForUpdate(o.getEventId()).orElseThrow();
                Instant now = Instant.now();
                String bookingBlock = bookingBlockCode(existingEvent, now);
                if (bookingBlock != null)
                    throw new ApiException(HttpStatus.CONFLICT, bookingBlock, bookingBlockMessage(bookingBlock));
            }
            if(!reservations.findByOrderIdOrderByIdAsc(o.getId()).stream().allMatch(r->r.getStatus()==Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(Instant.now())))
                throw new ApiException(HttpStatus.CONFLICT,"RESERVATION_EXPIRED","The ticket reservation for this order has expired. Start a new checkout.");
            String checkoutSessionToken = newCheckoutSessionToken();
            o.setCheckoutSessionHash(hashCheckoutSession(checkoutSessionToken));
            orders.saveAndFlush(o);
            EnterpriseLog.info(log, "order.checkout.idempotent_reuse", "event.category", "order", "order.id", o.getId(), "order.number", o.getOrderNumber());
            return new LocalCheckout(o.getId(), checkoutSessionToken);
        }
        Event event=events.findByPublicId(request.eventId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event not found"));
        Instant now = Instant.now();
        String bookingBlock = bookingBlockCode(event, now);
        if (bookingBlock != null)
            throw new ApiException(HttpStatus.CONFLICT, bookingBlock, bookingBlockMessage(bookingBlock));

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
            TicketType locked=reservationService.reserve(tt.getId(),merged.get(id));
            // ReservationService locks the ticket-type row. Validate the locked snapshot, not the stale
            // pre-lock entity, so price/status/quantity changes cannot bleed into the order.
            validateTicketType(locked,merged.get(id));
            lockedTypes.add(locked);
            total=Math.addExact(total,Math.multiplyExact(locked.getPriceMinorUnits(),merged.get(id)));
        }
        if(total<=0) throw new ApiException(HttpStatus.CONFLICT,"FREE_CHECKOUT_UNSUPPORTED","Zero-value checkout is not enabled for this deployment");
        // Take the event lock only for the final inventory/order commit. This preserves the cancellation/update
        // race guarantee without serializing all buyers on the same event row for the entire checkout transaction.
        Event lockedEvent=events.findByIdForUpdate(event.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event not found"));
        Instant commitNow = Instant.now();
        String commitBookingBlock = bookingBlockCode(lockedEvent, commitNow);
        if (commitBookingBlock != null) {
            throw new ApiException(HttpStatus.CONFLICT, commitBookingBlock, bookingBlockMessage(commitBookingBlock));
        }
        Instant expires=Instant.now().plus(props.reservation().hold());
        Order order=new Order(); order.setOrderNumber(orderNumber()); order.setEventId(lockedEvent.getId()); order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail().trim().toLowerCase(java.util.Locale.ROOT)); order.setCustomerPhone(normalizePhone(request.customerPhone())); order.setTotalMinorUnits(total); order.setCurrency(lockedEvent.getCurrency());

        order.setStatus(Enums.OrderStatus.AWAITING_PAYMENT); order.setIdempotencyKey(request.idempotencyKey());
        String checkoutSessionToken = newCheckoutSessionToken();
        order.setCheckoutSessionHash(hashCheckoutSession(checkoutSessionToken));
        order=orders.saveAndFlush(order);
        EnterpriseLog.info(log, "order.created", "event.category", "order", "order.id", order.getId(), "order.number", order.getOrderNumber(), "event.id", order.getEventId(), "order.total_minor", order.getTotalMinorUnits(), "order.status", order.getStatus().name());
        for(TicketType tt:lockedTypes){
            int q=merged.get(tt.getPublicId());
            OrderItem oi=new OrderItem(); oi.setOrderId(order.getId()); oi.setTicketTypeId(tt.getId()); oi.setQuantity(q); oi.setUnitPriceMinor(tt.getPriceMinorUnits()); oi.setSubtotalMinor(Math.multiplyExact(tt.getPriceMinorUnits(),q)); items.save(oi);
            TicketReservation r=new TicketReservation(); r.setOrderId(order.getId()); r.setTicketTypeId(tt.getId()); r.setQuantity(q); r.setStatus(Enums.ReservationStatus.HELD); r.setExpiresAt(expires); reservations.save(r);
        }
        Payment p=new Payment(); p.setOrderId(order.getId()); p.setAmountMinor(total); p.setCurrency(order.getCurrency()); p.setProvider(event.getPaymentProvider()); p.setStatus(Enums.PaymentStatus.CREATED); p.setRazorpayOrderState("NOT_CREATED"); p.setRazorpayOrderAttempts(0); payments.saveAndFlush(p);
        audit.log(null,"ORDER_CREATED","ORDER",order.getPublicId().toString(),null);
        return new LocalCheckout(order.getId(), checkoutSessionToken);
    }

    public CheckoutResponse provisionPaymentOrder(Long orderId){ EnterpriseLog.debug(log, "payment.order.provision.requested", "event.category", "payment", "order.id", orderId); return provisionPaymentOrder(orderId, null); }

    private CheckoutResponse provisionPaymentOrder(Long orderId, String checkoutSessionToken){
        var lock = locks.tryAcquire("payment-order:" + orderId, java.time.Duration.ofSeconds(90));
        if(!lock.acquired()) throw new ApiException(HttpStatus.CONFLICT,"CHECKOUT_BUSY","Checkout is being initialized; please retry");
        try {
            ProviderOrderContext context = tx.execute(status -> {
                Order o=orders.findByIdForUpdate(orderId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
                Payment p=payments.findByOrderId(o.getId()).orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"PAYMENT_MISSING","Payment record missing"));
                if(o.getStatus()==Enums.OrderStatus.CONFIRMED) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Order is already confirmed");
                Event event = events.findByIdForUpdate(o.getEventId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","Event not found"));
                Instant now = Instant.now();
                List<TicketReservation> heldReservations = reservations.findByOrderIdOrderByIdAsc(o.getId());
                boolean reservationLive = !heldReservations.isEmpty()
                        && heldReservations.stream().allMatch(r -> r.getStatus() == Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(now));
                String bookingBlock = bookingBlockCode(event, now);
                boolean eventOpen = bookingBlock == null;
                if(!reservationLive || !eventOpen){
                    reservationService.releaseOrder(o.getId());
                    o.setStatus(eventOpen ? Enums.OrderStatus.EXPIRED : Enums.OrderStatus.CANCELLED);
                    p.setProviderLastError(eventOpen ? "Reservation expired before payment order creation" : bookingBlockMessage(bookingBlock));
                    p.setRazorpayOrderState(p.getProviderOrderId()==null ? "NOT_CREATED" : p.getRazorpayOrderState());
                    payments.save(p);
                    return new ProviderOrderContext(o.getId(),o.getPublicId(),o.getOrderNumber(),p.getProviderOrderId(),p.getAmountMinor(),p.getCurrency(),minReservationExpiry(o.getId()),false, eventOpen ? "RESERVATION_EXPIRED" : bookingBlock, p.getRazorpayOrderState(),o.getCustomerName(),o.getCustomerEmail(),o.getCustomerPhone(),p.getProvider());
                }
                if(p.getProviderOrderId()!=null) return new ProviderOrderContext(o.getId(),o.getPublicId(),o.getOrderNumber(),p.getProviderOrderId(),p.getAmountMinor(),p.getCurrency(),minReservationExpiry(o.getId()),true,null, p.getRazorpayOrderState(),o.getCustomerName(),o.getCustomerEmail(),o.getCustomerPhone(),p.getProvider());
                p.setRazorpayOrderState("CREATING"); p.setRazorpayOrderAttempts(p.getRazorpayOrderAttempts()+1); payments.save(p);
                return new ProviderOrderContext(o.getId(),o.getPublicId(),o.getOrderNumber(),null,p.getAmountMinor(),p.getCurrency(),minReservationExpiry(o.getId()),true,null,p.getRazorpayOrderState(),o.getCustomerName(),o.getCustomerEmail(),o.getCustomerPhone(),p.getProvider());
            });
            if(context == null) throw new ApiException(HttpStatus.CONFLICT,"CHECKOUT_INITIALIZATION_FAILED","Checkout could not be initialized");
            if(!context.payable()){
                throw new ApiException(HttpStatus.CONFLICT,context.blockCode(),"The checkout can no longer accept payment. Please start a new checkout.");
            }
            if(context.providerOrderId()!=null){
                // A stale local provider id is recoverable: clear only when the provider explicitly
                // confirms that resource does not exist. Transient provider failures remain fail-closed.
                try {
                    var providerOrder=gateways.forProvider(context.provider()).fetchOrder(context.providerOrderId());
                    if(providerOrder.amount()!=context.amountMinor() || !context.currency().equalsIgnoreCase(providerOrder.currency()) || !context.orderNumber().equals(providerOrder.receipt()))
                        throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_MISMATCH","The provider order does not match this checkout");
                if("paid".equalsIgnoreCase(providerOrder.status())){
                    var providerPayments = gateways.forProvider(context.provider()).fetchPaymentsForOrder(providerOrder.id());
                    var providerPayment = selectProviderPaymentForReconciliation(
                            payments.findByOrderId(context.orderId()).orElseThrow(), providerPayments);
                    if(providerPayment.isPresent()){
                        var pp=providerPayment.get();
                        if(reconcileProviderRefundsIfPresent(context.orderId(),pp)) {
                            throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_REFUND_RECONCILIATION_PENDING","A provider refund is present; ticket issuance is paused until refund reconciliation completes");
                        } else {
                            var reconciled=reconcileCapturedPayment(findPaymentId(context.orderId()),pp);
                            if("FULFILLED".equals(reconciled.status())) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Payment was already completed; retrieve the ticket from order recovery");
                            if("REFUND_PENDING".equals(reconciled.status())) completeQueuedRefundIfNeeded(findPaymentId(context.orderId()),"Provider payment captured after checkout recovery");
                        }
                    }
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_RECOVERY_PENDING","This payment session is already marked paid at the provider. Retrieve the ticket from order recovery.");
                }
                    return responseForCheckout(context, checkoutSessionToken);
                } catch (ApiException ex) {
                    if (ex.status() != HttpStatus.NOT_FOUND || !"PAYMENT_PROVIDER_NOT_FOUND".equals(ex.code())) throw ex;
                    tx.executeWithoutResult(s -> payments.findByOrderId(context.orderId()).ifPresent(p -> {
                        p.setProviderOrderId(null);
                        p.setRazorpayOrderId(null);
                        p.setProviderPublicKey(null);
                        p.setProviderSessionId(null);
                        p.setRazorpayOrderState("NOT_CREATED");
                        p.setProviderLastError(null);
                        payments.save(p);
                    }));
                    // Continue into receipt-based recovery/creation below.
                }
            }
            Optional<PaymentGatewayProvider.ProviderOrder> recovered;
            try { recovered = gateways.forProvider(context.provider()).findOrderByReceipt(context.orderNumber()); }
            catch (RuntimeException ex) {
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setRazorpayOrderState("RECOVERY_PENDING");p.setProviderLastError(ex.getClass().getSimpleName());payments.save(p);}); });
                throw ex;
            }
            if(recovered.isPresent()) {
                var providerOrder=recovered.get();
                if(providerOrder.amount()!=context.amountMinor() || !context.currency().equalsIgnoreCase(providerOrder.currency()) || !context.orderNumber().equals(providerOrder.receipt()))
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_MISMATCH","A provider order with this receipt does not match this checkout");
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setProviderOrderId(providerOrder.id()); if(p.getProvider()==Enums.PaymentProvider.RAZORPAY)p.setRazorpayOrderId(providerOrder.id()); p.setProviderPublicKey(providerOrder.publicKey());p.setProviderSessionId(providerOrder.sessionId());p.setRazorpayOrderState("READY");paymentStateMachine.transition(p,Enums.PaymentStatus.PENDING);p.setProviderLastError(null);payments.save(p);}); });
                if("paid".equalsIgnoreCase(providerOrder.status())) {
                    var recoveredPayments = gateways.forProvider(context.provider()).fetchPaymentsForOrder(providerOrder.id());
                    var recoveredPayment = selectProviderPaymentForReconciliation(
                            payments.findByOrderId(context.orderId()).orElseThrow(), recoveredPayments);
                    if(recoveredPayment.isPresent()) {
                        var rpPayment=recoveredPayment.get();
                        Long localPaymentId=payments.findByOrderId(context.orderId()).orElseThrow().getId();
                        if(reconcileProviderRefundsIfPresent(localPaymentId, rpPayment)) {
                            throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_REFUND_RECONCILIATION_PENDING","A provider refund is present; ticket issuance is paused until refund reconciliation completes.");
                        } else {
                            var reconciled=reconcileCapturedPayment(localPaymentId, rpPayment);
                            if("FULFILLED".equals(reconciled.status())) throw new ApiException(HttpStatus.CONFLICT,"ORDER_ALREADY_CONFIRMED","Payment was already completed; retrieve the ticket from order recovery");
                            if("REFUND_PENDING".equals(reconciled.status())) {
                                completeQueuedRefundIfNeeded(localPaymentId,"Provider payment captured during receipt-based checkout recovery");
                            }
                        }
                    }
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_RECOVERY_PENDING","This payment session is already marked paid at the provider. Please use order recovery while we finish reconciliation.");
                }
                return new CheckoutResponse(context.publicId().toString(),context.orderNumber(),context.provider().name(),providerOrder.id(),providerOrder.publicKey(),providerOrder.sessionId(),context.amountMinor(),context.currency(),context.reservationExpiresAt(),providerCheckoutMode(context.provider()),checkoutSessionToken,props.checkout().sessionTtl().toSeconds());
            }
            if(!"NOT_CREATED".equalsIgnoreCase(context.providerOrderState())) {
                // The lookup above is authoritative: an empty result means the provider does not
                // have an order for this merchant receipt, so it is safe to re-enter creation.
                // We must not permanently brick checkout merely because a previous network call
                // timed out after local state was moved to RECOVERY_PENDING/CREATING.
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{
                    p.setRazorpayOrderState("NOT_CREATED");
                    p.setProviderLastError(null);
                    payments.save(p);
                }); });
            }
            PaymentGatewayProvider.ProviderOrder rp;
            try { rp=gateways.forProvider(context.provider()).createOrder(context.amountMinor(),context.currency(),context.orderNumber(), context.customerName(), context.customerEmail(), context.customerPhone()); }
            catch (RuntimeException ex) {
                // A timeout may mean Razorpay created the order even though the response was lost. Re-check by receipt before retrying.
                try {
                    var retryLookup=gateways.forProvider(context.provider()).findOrderByReceipt(context.orderNumber());
                    if(retryLookup.isPresent()) {
                        var providerOrder=retryLookup.get();
                        if(providerOrder.amount()==context.amountMinor()
                                && context.currency().equalsIgnoreCase(providerOrder.currency())
                                && context.orderNumber().equals(providerOrder.receipt())) {
                            tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setProviderOrderId(providerOrder.id()); if(p.getProvider()==Enums.PaymentProvider.RAZORPAY)p.setRazorpayOrderId(providerOrder.id()); p.setProviderPublicKey(providerOrder.publicKey());p.setProviderSessionId(providerOrder.sessionId());p.setRazorpayOrderState("READY");paymentStateMachine.transition(p,Enums.PaymentStatus.PENDING);p.setProviderLastError(null);payments.save(p);}); });
                            return new CheckoutResponse(context.publicId().toString(),context.orderNumber(),context.provider().name(),providerOrder.id(),providerOrder.publicKey(),providerOrder.sessionId(),context.amountMinor(),context.currency(),context.reservationExpiresAt(),providerCheckoutMode(context.provider()),checkoutSessionToken,props.checkout().sessionTtl().toSeconds());
                        }
                    }
                } catch (RuntimeException ignored) { }
                tx.executeWithoutResult(s->{ payments.findByOrderId(context.orderId()).ifPresent(p->{p.setRazorpayOrderState("RECOVERY_PENDING");p.setProviderLastError(ex.getClass().getSimpleName());payments.save(p);}); });
                throw ex;
            }
            tx.executeWithoutResult(s->{
                Payment p=payments.findByOrderId(context.orderId()).orElseThrow();
                if(p.getProviderOrderId()==null){p.setProviderOrderId(rp.id()); if(p.getProvider()==Enums.PaymentProvider.RAZORPAY)p.setRazorpayOrderId(rp.id()); p.setProviderPublicKey(rp.publicKey());p.setProviderSessionId(rp.sessionId());p.setRazorpayOrderState("READY");paymentStateMachine.transition(p,Enums.PaymentStatus.PENDING);p.setProviderLastError(null);payments.save(p);}
            });
            return new CheckoutResponse(context.publicId().toString(),context.orderNumber(),context.provider().name(),rp.id(),rp.publicKey(),rp.sessionId(),context.amountMinor(),context.currency(),context.reservationExpiresAt(),providerCheckoutMode(context.provider()),checkoutSessionToken,props.checkout().sessionTtl().toSeconds());
        } finally { lock.close(); }
    }

    public VerifyResponse verifyAndConfirm(VerifyRequest req){
        EnterpriseLog.info(log, "payment.verification.started", "event.category", "payment", "provider.order.id", req.providerOrderId());
        String client=req.clientKey()==null?"unknown":req.clientKey();
        Duration verifyWindow=Duration.ofSeconds(props.rateLimit().windowSeconds());
        String orderKey=req.providerOrderId()==null?"unknown":hashForRateLimit(req.providerOrderId().trim());
        if(!rateLimits.allow("payment-verify:"+client,Math.max(5,props.rateLimit().publicCheckoutPerWindow()/2),verifyWindow)
                || !rateLimits.allow("payment-verify-order:"+orderKey,5,verifyWindow))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Too many payment verification attempts");
        if(req.checkoutSessionToken()==null || req.checkoutSessionToken().isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED,"CHECKOUT_SESSION_REQUIRED","Checkout session is missing or expired");
        Payment p=payments.findByProviderOrderId(req.providerOrderId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment session not found"));
        var lock=locks.tryAcquire("checkout-verify:"+p.getId(),java.time.Duration.ofSeconds(90));
        if(!lock.acquired()) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_VERIFY_BUSY","Payment verification is already in progress; please retry");
        try {
            if(!validCheckoutSessionToken(req.checkoutSessionToken()))
                throw new ApiException(HttpStatus.UNAUTHORIZED,"CHECKOUT_SESSION_INVALID","Checkout session is invalid or expired");
            Payment lockedPayment=payments.findById(p.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment session not found"));
            Order verifiedOrder=orders.findById(lockedPayment.getOrderId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
            if(verifiedOrder.getCheckoutSessionHash()==null || !MessageDigest.isEqual(verifiedOrder.getCheckoutSessionHash().getBytes(StandardCharsets.US_ASCII),hashCheckoutSession(req.checkoutSessionToken()).getBytes(StandardCharsets.US_ASCII)))
                throw new ApiException(HttpStatus.UNAUTHORIZED,"CHECKOUT_SESSION_INVALID","Checkout session is invalid or expired");
            // The payment provider webhook can win the race and fulfill this order before the browser
            // returns from the hosted checkout. Verification must therefore be idempotent: once the
            // order is confirmed, never attempt a backwards state transition such as COMPLETED -> CAPTURED.
            if(verifiedOrder.getStatus()==Enums.OrderStatus.CONFIRMED) return response(verifiedOrder);
            PaymentGatewayProvider gateway=gateways.forPayment(lockedPayment);
            PaymentGatewayProvider.ProviderPayment provider;
            if(lockedPayment.getProvider()==Enums.PaymentProvider.RAZORPAY){
                if(req.providerPaymentId()==null||req.providerPaymentId().isBlank()||!gateway.verifyPaymentSignature(lockedPayment.getProviderOrderId(),req.providerPaymentId(),req.providerSignature()))
                    throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAYMENT_SIGNATURE","Payment verification failed");
                provider=gateway.fetchPayment(req.providerPaymentId());
            } else {
                List<PaymentGatewayProvider.ProviderPayment> cashfreePayments = gateway.fetchPaymentsForOrder(lockedPayment.getProviderOrderId());
                cashfreePayments.forEach(attempt -> recordProviderAttempt(lockedPayment, attempt));
                Optional<PaymentGatewayProvider.ProviderPayment> captured = selectCapturedPayment(lockedPayment, req.providerPaymentId(), cashfreePayments);
                if (captured.isEmpty()) {
                    // Cashfree redirects here for both success and user-cancelled/failed attempts.
                    // A missing captured payment is therefore a normal non-final state, not a client error.
                    Order pendingOrder=orders.findById(lockedPayment.getOrderId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
                    boolean inProgress = cashfreePayments.stream().anyMatch(x ->
                            !x.captured()
                                    && !"failed".equalsIgnoreCase(x.status())
                                    && !"cancelled".equalsIgnoreCase(x.status())
                                    && !"user_dropped".equalsIgnoreCase(x.status()));
                    boolean failed = !inProgress && !cashfreePayments.isEmpty()
                            && cashfreePayments.stream().allMatch(x ->
                                "failed".equalsIgnoreCase(x.status())
                                        || "cancelled".equalsIgnoreCase(x.status())
                                        || "user_dropped".equalsIgnoreCase(x.status()));
                    // A failed/abandoned earlier attempt must not hide a newer pending attempt.
                    EnterpriseLog.debug(log, "payment.verification.pending", "event.category", "payment", "order.id", pendingOrder.getId(), "order.number", pendingOrder.getOrderNumber(), "payment.status", failed ? "CANCELLED" : "PENDING");
                    return new VerifyResponse(pendingOrder.getPublicId().toString(), pendingOrder.getOrderNumber(),
                            failed ? "CANCELLED" : "PENDING", List.of());
                }
                provider=captured.get();
            }
            tx.executeWithoutResult(status->{
                Payment current=payments.findByIdForUpdate(lockedPayment.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment session not found"));
                if(current.getStatus()==Enums.PaymentStatus.REFUNDED || current.getStatus()==Enums.PaymentStatus.REFUND_PENDING)
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_NOT_REUSABLE","This payment is already closed for ticket issuance");
                validateProviderPayment(current,provider);
                if(current.getProviderPaymentId()!=null && !Objects.equals(current.getProviderPaymentId(),provider.id()))
                    throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_ID_MISMATCH","A different payment is already associated with this order");
                current.setProviderPaymentId(provider.id()); if(current.getProvider()==Enums.PaymentProvider.RAZORPAY)current.setRazorpayPaymentId(provider.id());
                current.setRazorpaySignature(req.providerSignature());
                paymentStateMachine.transition(current,Enums.PaymentStatus.CAPTURED);
                payments.save(current);
            });
            CaptureReconciliation result=reconcileCapturedPayment(lockedPayment.getId(),provider);
            if ("REFUND_PENDING".equals(result.status())) {
                completeQueuedRefundIfNeeded(lockedPayment.getId(), "Reservation expired, event closed, or order no longer payable before payment capture");
                Order pendingOrder=orders.findByOrderNumber(result.orderNumber()).orElseThrow();
                tx.executeWithoutResult(status->{
                    orders.findByIdForUpdate(pendingOrder.getId()).ifPresent(order -> { order.setCheckoutSessionHash(null); orders.save(order); });
                });
                EnterpriseLog.warn(log, "payment.verification.refund_pending", "event.category", "payment",
                        "order.id", pendingOrder.getId(), "order.number", pendingOrder.getOrderNumber(), "refund.required", true);
                return new VerifyResponse(pendingOrder.getPublicId().toString(), result.orderNumber(), "REFUND_PENDING", List.of());
            }
            Order o=orders.findByOrderNumber(result.orderNumber()).orElseThrow();
            tx.executeWithoutResult(status->{
                Order lockedOrder=orders.findByIdForUpdate(o.getId()).orElseThrow();
                if(lockedOrder.getCheckoutSessionHash()==null) return;
                if(!validCheckoutSessionToken(req.checkoutSessionToken()) || !MessageDigest.isEqual(lockedOrder.getCheckoutSessionHash().getBytes(StandardCharsets.US_ASCII),hashCheckoutSession(req.checkoutSessionToken()).getBytes(StandardCharsets.US_ASCII)))
                    throw new ApiException(HttpStatus.UNAUTHORIZED,"CHECKOUT_SESSION_INVALID","Checkout session is invalid or expired");
                lockedOrder.setCheckoutSessionHash(null);
                orders.save(lockedOrder);
            });
            return response(o);
        } finally { lock.close(); }
    }

    public CaptureReconciliation reconcileCapturedPayment(Long paymentId, PaymentGatewayProvider.ProviderPayment provider){
        return tx.execute(status -> {
            Payment p=payments.findByIdForUpdate(paymentId).orElseThrow();
            if(p.getStatus()==Enums.PaymentStatus.REFUNDED) return new CaptureReconciliation(findOrderNumber(p.getOrderId()),"REFUNDED");
            validateProviderPayment(p, provider);
            Order o=orders.findByIdForUpdate(p.getOrderId()).orElseThrow();
            Event event=events.findById(o.getEventId()).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,"EVENT_NOT_FOUND","Event no longer exists"));
            if(p.getProviderPaymentId()!=null && !Objects.equals(p.getProviderPaymentId(), provider.id()))
                throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_ID_MISMATCH","A different payment is already associated with this order");
            recordProviderAttempt(p, provider);
            p.setProviderPaymentId(provider.id()); if(p.getProvider()==Enums.PaymentProvider.RAZORPAY)p.setRazorpayPaymentId(provider.id());
            if(p.getStatus()==Enums.PaymentStatus.REFUND_PENDING) {
                // A provider capture can legitimately arrive after local compensation was queued.
                // Never regress REFUND_PENDING to CAPTURED; preserve the compensation state.
                payments.save(p);
                return new CaptureReconciliation(o.getOrderNumber(),"REFUND_PENDING");
            }
            if(o.getStatus()==Enums.OrderStatus.CONFIRMED){
                paymentStateMachine.transition(p,Enums.PaymentStatus.COMPLETED);
                payments.save(p);
                return new CaptureReconciliation(o.getOrderNumber(),"FULFILLED");
            }
            List<TicketReservation> rs=reservations.findByOrderIdForUpdate(o.getId());
            Instant now=Instant.now();
            boolean reservationValid=!rs.isEmpty() && rs.stream().allMatch(r->r.getStatus()==Enums.ReservationStatus.HELD && r.getExpiresAt().isAfter(now));
            boolean eventOpen=PublicEventService.bookingWindowOpen(event, now);
            boolean orderPayable=o.getStatus()==Enums.OrderStatus.CREATED || o.getStatus()==Enums.OrderStatus.AWAITING_PAYMENT;
            if(!reservationValid || !eventOpen || !orderPayable){
                // A provider capture may legitimately arrive while the local payment is still
                // PENDING/FAILED/CANCELLED. Record the financial fact first, then queue compensation.
                transitionToCapturedBeforeRefund(p);
                paymentStateMachine.transition(p,Enums.PaymentStatus.REFUND_PENDING); payments.save(p);
                reservationService.releaseOrder(o.getId());
                if(o.getStatus()!=Enums.OrderStatus.CONFIRMED) o.setStatus(Enums.OrderStatus.CANCELLED);
                // Return REFUND_PENDING so the caller queues compensation immediately after this transaction commits.
                // The reconciliation sweep is the durable fallback if the caller is interrupted.
                return new CaptureReconciliation(o.getOrderNumber(),"REFUND_PENDING");
            }
            paymentStateMachine.transition(p,Enums.PaymentStatus.CAPTURED); payments.save(p);
            fulfillOrderLocked(o,p,rs);
            return new CaptureReconciliation(o.getOrderNumber(),"FULFILLED");
        });
    }

    public void completeQueuedRefundIfNeeded(Long paymentId,String reason){ refundService.queueCapturedPaymentRefund(paymentId,reason); }

    public boolean reconcileProviderRefundsIfPresent(Long paymentId, PaymentGatewayProvider.ProviderPayment providerPayment){
        if (providerPayment == null || providerPayment.id() == null || providerPayment.id().isBlank()) return false;
        Payment local = payments.findById(paymentId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_FOUND", "Payment for provider refund was not found"));
        boolean refundIndicated = providerPayment.amountRefunded() > 0 || "refunded".equalsIgnoreCase(providerPayment.status());
        if (!refundIndicated) return false;
        if (local.getProvider() != Enums.PaymentProvider.RAZORPAY) {
            // Cashfree refund webhooks are reconciled by CashfreeWebhookService. Keep polling
            // conservative so a provider-specific refund API is never called with a mismatched ID.
            return false;
        }
        List<PaymentGatewayProvider.ProviderRefund> providerRefunds = gateways.forPayment(local).fetchRefundsForPayment(providerPayment.id());
        if (providerRefunds == null || providerRefunds.isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REFUND_LEDGER_UNAVAILABLE",
                    "Provider reports a refund but no refund transaction could be retrieved; reconciliation is deferred");
        }
        boolean activeRefundPresent = false;
        for (PaymentGatewayProvider.ProviderRefund providerRefund : providerRefunds) {
            if (providerRefund == null || providerRefund.id() == null || providerRefund.id().isBlank() || providerRefund.amount() <= 0) continue;
            refundService.reconcileProviderRefund(paymentId, providerRefund.id(), providerRefund.status(), providerRefund.amount(), providerRefund.receipt());
            String status = providerRefund.status() == null ? "" : providerRefund.status().trim().toLowerCase(Locale.ROOT);
            if (!(status.equals("failed") || status.equals("failure") || status.equals("cancelled") || status.equals("canceled") || status.equals("rejected")
                    || status.endsWith("_failed") || status.endsWith("_failure") || status.endsWith("_cancelled") || status.endsWith("_canceled") || status.endsWith("_rejected"))) {
                activeRefundPresent = true;
            }
        }
        return activeRefundPresent;
    }

    /**
     * Provider truth is authoritative once a payment is observed as captured/refunded.
     * Local payment attempts can still be PENDING/FAILED/CANCELLED because the webhook,
     * browser verification and reconciliation paths are intentionally asynchronous.
     */
    private void transitionToCapturedBeforeRefund(Payment p) {
        if (p.getStatus() != Enums.PaymentStatus.CAPTURED
                && p.getStatus() != Enums.PaymentStatus.COMPLETED
                && p.getStatus() != Enums.PaymentStatus.REFUND_PENDING
                && p.getStatus() != Enums.PaymentStatus.REFUNDED) {
            paymentStateMachine.transition(p, Enums.PaymentStatus.CAPTURED);
        }
    }

    private void fulfillOrderLocked(Order o, Payment p, List<TicketReservation> rs){
        List<Ticket> existing=tickets.findByOrderIdOrderByTicketNumberAsc(o.getId());
        if(!existing.isEmpty()){o.setStatus(Enums.OrderStatus.CONFIRMED);paymentStateMachine.transition(p,Enums.PaymentStatus.COMPLETED);return;}
        for(TicketReservation r:rs){ if(r.getStatus()!=Enums.ReservationStatus.HELD || !r.getExpiresAt().isAfter(Instant.now())) throw new ApiException(HttpStatus.CONFLICT,"RESERVATION_EXPIRED","Ticket reservation expired"); reservationService.confirmSale(r.getTicketTypeId(),r.getQuantity()); r.setStatus(Enums.ReservationStatus.CONFIRMED); }
        List<OrderItem> orderItems=items.findByOrderId(o.getId());
        for(OrderItem oi:orderItems){
            for(int i=0;i<oi.getQuantity();i++){
                Ticket t=new Ticket();t.setOrderId(o.getId());t.setOrderItemId(oi.getId());t.setEventId(o.getEventId());t.setTicketTypeId(oi.getTicketTypeId());t.setAttendeeName(o.getCustomerName());t.setTicketNumber(ticketNumber());t.setStatus(Enums.TicketStatus.ISSUED);String credential=qr.credentialFor(t.getPublicId());t.setQrCredentialHash(qr.hash(credential));tickets.save(t);
            }
        }
        o.setStatus(Enums.OrderStatus.CONFIRMED);paymentStateMachine.transition(p,Enums.PaymentStatus.COMPLETED);audit.log(o.getUserId(),"TICKETS_ISSUED","ORDER",o.getPublicId().toString(),null);
        ticketMail.sendAfterCommit(o.getId()); // best-effort email after commit; never affects the sale
    }

    public void transitionPaymentForWebhook(Payment payment, Enums.PaymentStatus target){ paymentStateMachine.transition(payment,target); payments.save(payment); }

    public void failPayment(String providerOrderId,String reason){
        EnterpriseLog.warn(log, "payment.marked_failed", "event.category", "payment", "provider.order.id", providerOrderId, "reason", reason);
        tx.executeWithoutResult(status->{
            payments.findByProviderOrderId(providerOrderId).ifPresent(found->{
                Payment p=payments.findByIdForUpdate(found.getId()).orElseThrow();
                if(p.getStatus()==Enums.PaymentStatus.COMPLETED||p.getStatus()==Enums.PaymentStatus.CAPTURED||p.getStatus()==Enums.PaymentStatus.REFUND_PENDING||p.getStatus()==Enums.PaymentStatus.REFUNDED)return;
                // A failed payment attempt does not prove that the Razorpay order can no longer succeed.
                // Keep the order payable while recording the latest attempt failure; reconciliation will
                // determine the authoritative provider state later.
                paymentStateMachine.transition(p,Enums.PaymentStatus.PENDING);
                p.setFailureReason(reason);
                p.setRazorpayOrderState("READY");
                payments.save(p);
            });
        });
    }

    /** Persist every provider transaction reference without replacing the authoritative successful payment ID. */
    public void recordProviderAttempt(Payment payment, PaymentGatewayProvider.ProviderPayment provider) {
        if (provider == null || provider.id() == null || provider.id().isBlank()) return;
        String providerPaymentId = provider.id();
        PaymentAttempt existing = paymentAttempts.findByProviderPaymentId(providerPaymentId).orElse(null);
        Enums.PaymentStatus attemptStatus = toAttemptStatus(provider.status(), provider.captured());

        if (existing != null) {
            if (!Objects.equals(existing.getPaymentId(), payment.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_COLLISION",
                        "Provider payment reference is already associated with another order");
            }
            existing.setProviderOrderId(provider.orderId());
            existing.setStatus(attemptStatus);
            existing.setAmountMinor(provider.amount());
            existing.setCurrency(provider.currency());
            existing.setProviderMessage(provider.errorDescription());
            paymentAttempts.save(existing);
            return;
        }

        // Do not use saveAndFlush + catch here: PostgreSQL marks the surrounding transaction
        // rollback-only after a unique-key violation. ON CONFLICT keeps the race inside SQL.
        int inserted = paymentAttempts.insertIgnoreDuplicate(
                payment.getId(),
                payment.getProvider().name(),
                providerPaymentId,
                provider.orderId(),
                attemptStatus.name(),
                provider.amount(),
                provider.currency(),
                provider.errorDescription());

        if (inserted == 0) {
            PaymentAttempt concurrent = paymentAttempts.findByProviderPaymentId(providerPaymentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                            "PAYMENT_ATTEMPT_CONCURRENCY", "Provider payment attempt could not be reconciled"));
            if (!Objects.equals(concurrent.getPaymentId(), payment.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_COLLISION",
                        "Provider payment reference is already associated with another order");
            }
        }
    }

    private Enums.PaymentStatus toAttemptStatus(String status, boolean captured) {
        if (captured || "captured".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) return Enums.PaymentStatus.CAPTURED;
        if ("failed".equalsIgnoreCase(status)) return Enums.PaymentStatus.FAILED;
        if ("cancelled".equalsIgnoreCase(status) || "user_dropped".equalsIgnoreCase(status)) return Enums.PaymentStatus.CANCELLED;
        return Enums.PaymentStatus.PENDING;
    }

    public VerifyResponse response(Order o){
        List<TicketRef> refs=tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream()
                .map(t->new TicketRef(t.getPublicId(),t.getTicketNumber(),accessTokens.issue(t.getPublicId()))).toList();
        EnterpriseLog.info(log, "payment.verification.completed", "event.category", "payment",
                "order.id", o.getId(), "order.number", o.getOrderNumber(), "order.status", o.getStatus().name(),
                "ticket.count", refs.size());
        return new VerifyResponse(o.getPublicId().toString(),o.getOrderNumber(),o.getStatus().name(),refs);
    }
    public CheckoutResponse existingResponse(Order o){return provisionPaymentOrder(o.getId());}

    /**
     * Reconciles local payments for which provider-order creation did not complete.
     * Kept on the primary order service to avoid an extra Spring component/classpath
     * boundary that caused stale-class failures on Windows/OneDrive workspaces.
     */
    @Scheduled(fixedDelayString = "${app.razorpay.order-recovery-sweep:30000}")
    @ConditionalOnProperty(prefix="app.worker", name="enabled", havingValue="true", matchIfMissing=true)
    public void recoverMissingProviderOrders(){
        if (!workerEnabled) return;
        locks.withLock("job:provider-order-recovery", java.time.Duration.ofSeconds(55), this::recoverMissingProviderOrdersLocked);
    }
    private void recoverMissingProviderOrdersLocked(){
        // Bounded look-back window plus per-payment rotation: abandoned checkouts must not starve newer ones.
        Instant now = Instant.now();
        var candidates = payments.findMissingProviderOrderCandidates(
                List.of(Enums.PaymentStatus.CREATED, Enums.PaymentStatus.PENDING,
                        Enums.PaymentStatus.PAYMENT_INITIATED, Enums.PaymentStatus.AUTHORIZED),
                now.minus(Duration.ofHours(Math.max(1, recoveryWindowHours))),
                now.minusMillis(Math.max(0, recoveryRecheckMs)),
                org.springframework.data.domain.PageRequest.of(0, 100));
        for (var payment : candidates) {
            try {
                provisionPaymentOrder(payment.getOrderId());
            } catch (Exception ex) {
                EnterpriseLog.warn(log, "payment.provider_order_recovery.failed", "event.category", "payment", "payment.id", payment.getId(), "error.type", ex.getClass().getSimpleName());
            } finally {
                try { payments.markReconciled(payment.getId(), Instant.now()); } catch (Exception ignored) { }
            }
        }
    }
    private Optional<PaymentGatewayProvider.ProviderPayment> selectCapturedPayment(Payment localPayment, String requestedPaymentId, List<PaymentGatewayProvider.ProviderPayment> paymentsForOrder) {
        List<PaymentGatewayProvider.ProviderPayment> candidates = paymentsForOrder.stream()
                .filter(x -> x.amount() == localPayment.getAmountMinor() && localPayment.getCurrency().equalsIgnoreCase(x.currency()))
                .filter(x -> "captured".equalsIgnoreCase(x.status()) && x.captured())
                .toList();
        String requested = requestedPaymentId == null ? null : requestedPaymentId.trim();
        if (requested != null && !requested.isBlank()) {
            return candidates.stream().filter(x -> requested.equals(x.id())).findFirst();
        }
        if (localPayment.getProviderPaymentId() != null && !localPayment.getProviderPaymentId().isBlank()) {
            return candidates.stream().filter(x -> localPayment.getProviderPaymentId().equals(x.id())).findFirst();
        }
        if (candidates.size() == 1) return Optional.of(candidates.get(0));
        if (candidates.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MULTIPLE_CAPTURED_PAYMENTS", "Multiple captured payments were found for this order; use the provider transaction ID to reconcile the order");
        }
        return Optional.empty();
    }

    Optional<PaymentGatewayProvider.ProviderPayment> selectProviderPaymentForReconciliation(Payment localPayment, List<PaymentGatewayProvider.ProviderPayment> paymentsForOrder) {
        List<PaymentGatewayProvider.ProviderPayment> candidates = paymentsForOrder.stream()
                .filter(x -> x.amount() == localPayment.getAmountMinor() && localPayment.getCurrency().equalsIgnoreCase(x.currency()))
                .filter(x -> "captured".equalsIgnoreCase(x.status()) || "refunded".equalsIgnoreCase(x.status()))
                .toList();
        if (localPayment.getProviderPaymentId() != null && !localPayment.getProviderPaymentId().isBlank()) {
            return candidates.stream().filter(x -> localPayment.getProviderPaymentId().equals(x.id())).findFirst();
        }
        if (candidates.size() == 1) return Optional.of(candidates.get(0));
        if (candidates.size() > 1) {
            throw new ApiException(HttpStatus.CONFLICT, "MULTIPLE_CAPTURED_PAYMENTS", "Multiple provider payments were found for this order; reconcile using the provider transaction ID");
        }
        return Optional.empty();
    }

    public void validateProviderPayment(Payment p,PaymentGatewayProvider.ProviderPayment provider){
        if(provider.id()==null||!Objects.equals(p.getProviderOrderId(),provider.orderId())) throw new ApiException(HttpStatus.BAD_REQUEST,"PAYMENT_MISMATCH","Payment does not belong to this order");
        if(provider.amount()!=p.getAmountMinor()||!p.getCurrency().equalsIgnoreCase(provider.currency())) throw new ApiException(HttpStatus.BAD_REQUEST,"PAYMENT_AMOUNT_MISMATCH","Payment amount could not be verified");
        if(!"captured".equalsIgnoreCase(provider.status()) || !provider.captured()) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_NOT_CAPTURED","Payment has not been captured yet");
        if(provider.amountRefunded()>0) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PARTIALLY_REFUNDED","Payment has already been refunded and cannot be issued as a new ticket purchase");
    }
    public record PublicEventControllerTicket(UUID ticketId,String ticketNumber,String accessToken){}
    public record RecoveryOrder(String orderNumber,Enums.OrderStatus status,String eventName,List<PublicEventControllerTicket> tickets){}
    public RecoveryOrder findForRecovery(String reference,String email){
        String normalized=reference.trim();
        Order o;
        if(normalized.matches("(?i)^LK-[A-Z0-9]{12}$")) {
            o=orders.findByOrderNumber(normalized.toUpperCase()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
        } else {
            Optional<Payment> providerPayment = payments.findByProviderPaymentId(normalized);
            if (providerPayment.isPresent()) {
                o = orders.findById(providerPayment.get().getOrderId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
            } else {
                PaymentAttempt attempt = paymentAttempts.findByProviderPaymentId(normalized)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
                Payment localPayment = payments.findById(attempt.getPaymentId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
                o = orders.findById(localPayment.getOrderId())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found"));
            }
        }
        if(!o.getCustomerEmail().equalsIgnoreCase(email.trim()))throw new ApiException(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order not found");
        Event e=events.findById(o.getEventId()).orElseThrow();
        List<PublicEventControllerTicket> ts = o.getStatus() == Enums.OrderStatus.CONFIRMED
                ? tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream()
                    .filter(t -> t.getStatus() != Enums.TicketStatus.CANCELLED && t.getStatus() != Enums.TicketStatus.REFUNDED)
                    .map(t -> new PublicEventControllerTicket(t.getPublicId(), t.getTicketNumber(), accessTokens.issue(t.getPublicId())))
                    .toList()
                : List.of();
        return new RecoveryOrder(o.getOrderNumber(),o.getStatus(),e.getName(),ts);
    }
    private String bookingBlockCode(Event event, Instant now) {
        if (event == null || event.getStatus() != Enums.EventStatus.PUBLISHED) return "EVENT_CLOSED";
        Instant eventEnd = event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt();
        if (eventEnd == null || !eventEnd.isAfter(now)) return "EVENT_CLOSED";
        if (event.getBookingStartsAt() != null && event.getBookingStartsAt().isAfter(now)) return "BOOKING_NOT_STARTED";
        Instant bookingEnd = event.getBookingEndsAt() != null ? event.getBookingEndsAt() : eventEnd;
        if (!bookingEnd.isAfter(now)) return "BOOKING_CLOSED";
        return null;
    }

    private String bookingBlockMessage(String code) {
        if ("BOOKING_NOT_STARTED".equals(code)) return "Ticket sales for this event have not opened yet";
        if ("BOOKING_CLOSED".equals(code)) return "Ticket sales for this event are closed";
        return "This event is no longer open for sale";
    }

    private boolean sameCheckoutRequest(Order o, CheckoutRequest request){
        if(!Objects.equals(o.getCustomerName(),request.customerName().trim())) return false;
        if(!Objects.equals(o.getCustomerEmail(),request.customerEmail().trim().toLowerCase(java.util.Locale.ROOT))) return false;
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
    private ProviderOrderContext context(Long orderId){Order o=orders.findById(orderId).orElseThrow();Payment p=payments.findByOrderId(orderId).orElseThrow();return new ProviderOrderContext(orderId,o.getPublicId(),o.getOrderNumber(),p.getProviderOrderId(),p.getAmountMinor(),p.getCurrency(),minReservationExpiry(orderId),true,null,p.getRazorpayOrderState(),o.getCustomerName(),o.getCustomerEmail(),o.getCustomerPhone(),p.getProvider());}
    private CheckoutResponse responseForCheckout(ProviderOrderContext c, String checkoutSessionToken){var p=payments.findByOrderId(c.orderId()).orElseThrow();return new CheckoutResponse(c.publicId().toString(),c.orderNumber(),c.provider().name(),c.providerOrderId(),p.getProviderPublicKey(),p.getProviderSessionId(),c.amountMinor(),c.currency(),c.reservationExpiresAt(),providerCheckoutMode(c.provider()),checkoutSessionToken,props.checkout().sessionTtl().toSeconds());}
    private String providerCheckoutMode(Enums.PaymentProvider provider) {
        if (provider != Enums.PaymentProvider.CASHFREE || props.cashfree() == null) return null;
        String baseUrl = props.cashfree().baseUrl();
        return baseUrl != null && baseUrl.toLowerCase(Locale.ROOT).contains("sandbox") ? "sandbox" : "production";
    }

    private record LocalCheckout(Long orderId, String checkoutSessionToken){}
    private String newCheckoutSessionToken(){
        byte[] b=new byte[32]; secureRandom.nextBytes(b);
        long exp=Instant.now().plus(props.checkout().sessionTtl()).getEpochSecond();
        return "CS1."+Base64.getUrlEncoder().withoutPadding().encodeToString(b)+"."+exp;
    }
    private boolean validCheckoutSessionToken(String token){
        try{
            String[] p=token.split("\\.");
            if(p.length!=3 || !"CS1".equals(p[0]) || p[1].length()<40 || !p[1].matches("[A-Za-z0-9_-]+")) return false;
            long exp=Long.parseLong(p[2]);
            long now=Instant.now().getEpochSecond();
            return exp>=now && exp<=now+Math.max(60L,props.checkout().sessionTtl().toSeconds()+60L);
        }catch(Exception e){ return false; }
    }
    private String hashCheckoutSession(String token){
        if(token==null || token.isBlank()) return "";
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Checkout session hashing failed",e);}
    }

    private record ProviderOrderContext(Long orderId,UUID publicId,String orderNumber,String providerOrderId,long amountMinor,String currency,Instant reservationExpiresAt,boolean payable,String blockCode,String providerOrderState,String customerName,String customerEmail,String customerPhone,Enums.PaymentProvider provider){}
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
