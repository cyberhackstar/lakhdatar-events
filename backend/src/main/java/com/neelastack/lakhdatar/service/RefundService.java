package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.*;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RefundService {
    private final PaymentRepository payments; private final RefundRepository refunds; private final OrderRepository orders;
    private final TicketRepository tickets; private final TicketReservationService reservationService; private final EventRepository events;
    private final OrganizerMemberRepository members; private final PaymentGatewayRouter gateways; private final AuditService audit;
    private final TransactionTemplate tx; private final DistributedLockService locks; private final EventAccessService eventAccess;
    private final PaymentStateMachine paymentStateMachine;

    public RefundResult refund(UUID paymentPublicId,String reason,Long actorId,String role){
        PreparedRefund prepared=tx.execute(status->prepareRefund(paymentPublicId,reason,actorId,role,false));
        if(prepared==null) throw new ApiException(HttpStatus.CONFLICT,"REFUND_FAILED","Refund could not be prepared");
        if(prepared.alreadyQueued()) return new RefundResult(prepared.paymentPublicId(),prepared.refundPublicId().toString(),prepared.amountMinor(),"PROCESSING");
        processRefund(prepared.refundId()); Refund r=refunds.findById(prepared.refundId()).orElseThrow();
        return new RefundResult(prepared.paymentPublicId(),r.getProviderRefundId()!=null?r.getProviderRefundId():r.getPublicId().toString(),r.getAmountMinor(),r.getStatus().name());
    }
    PreparedRefund queueCapturedPaymentRefund(Long paymentId,String reason){PreparedRefund p=tx.execute(s->{Payment x=payments.findById(paymentId).orElseThrow();return prepareRefund(x.getPublicId(),reason,null,"SYSTEM",true);});if(p!=null&&!p.alreadyQueued())processRefund(p.refundId());return p;}
    PreparedRefund queueCapturedPaymentRefundOnly(Long paymentId,String reason){return tx.execute(s->{Payment x=payments.findById(paymentId).orElseThrow();return prepareRefund(x.getPublicId(),reason,null,"SYSTEM",true);});}

    private PreparedRefund prepareRefund(UUID publicId,String reason,Long actorId,String role,boolean allowSystem){
        String rr=reason==null?"Event cancellation":reason.trim(); if(rr.length()>500)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REFUND_REASON","Refund reason is too long");
        Payment p=payments.findByPublicId(publicId).flatMap(x->payments.findByIdForUpdate(x.getId())).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment not found"));
        if(p.getStatus()!=Enums.PaymentStatus.CAPTURED&&p.getStatus()!=Enums.PaymentStatus.COMPLETED&&p.getStatus()!=Enums.PaymentStatus.REFUND_PENDING)throw new ApiException(HttpStatus.CONFLICT,"REFUND_NOT_ALLOWED","Payment is not captured");
        Order o=orders.findById(p.getOrderId()).orElseThrow(); Event event=events.findById(o.getEventId()).orElseThrow();
        if(!allowSystem && !isFinancialRefundApprover(role, event.getOrganizerId(), actorId))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Not authorized to refund this payment");
        var existing=refunds.findByPaymentId(p.getId()); if(existing.isPresent()){Refund r=existing.get();if(r.getStatus()==Enums.RefundStatus.COMPLETED)throw new ApiException(HttpStatus.CONFLICT,"REFUND_EXISTS","A refund already exists for this payment");if(r.getStatus()==Enums.RefundStatus.FAILED){r.setStatus(Enums.RefundStatus.PROCESSING);r.setLastError(null);return new PreparedRefund(p.getPublicId(),r.getId(),r.getPublicId(),r.getAmountMinor(),false);}return new PreparedRefund(p.getPublicId(),r.getId(),r.getPublicId(),r.getAmountMinor(),true);}
        if(tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).stream().anyMatch(t->t.getStatus()==Enums.TicketStatus.CHECKED_IN))throw new ApiException(HttpStatus.CONFLICT,"REFUND_NOT_ALLOWED","A ticket has already been checked in");
        Refund r=new Refund();r.setPaymentId(p.getId());r.setAmountMinor(p.getAmountMinor());r.setReason(rr);r.setStatus(Enums.RefundStatus.PROCESSING);r.setAttemptCount(0);refunds.saveAndFlush(r);
        paymentStateMachine.transition(p,Enums.PaymentStatus.REFUND_PENDING);o.setStatus(Enums.OrderStatus.CANCELLED);tickets.findByOrderIdOrderByTicketNumberAsc(o.getId()).forEach(t->{if(t.getStatus()!=Enums.TicketStatus.CHECKED_IN)t.setStatus(Enums.TicketStatus.CANCELLED);});reservationService.releaseOrder(o.getId());audit.log(actorId,"REFUND_QUEUED","PAYMENT",p.getPublicId().toString(),null);
        return new PreparedRefund(p.getPublicId(),r.getId(),r.getPublicId(),r.getAmountMinor(),false);
    }

    @Transactional
    public void reconcileProviderRefund(Long paymentId, String providerRefundId, String providerStatus, long amountMinor) {
        Refund r = refunds.findByPaymentId(paymentId).orElseThrow(() ->
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REFUND_NOT_LINKED", "Provider refund arrived before a local refund record was created"));
        if (providerRefundId == null || providerRefundId.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND_PROVIDER_ID_REQUIRED", "Provider refund identifier is required");
        Refund providerLinked = refunds.findByProviderRefundId(providerRefundId).orElse(null);
        if (providerLinked != null && !providerLinked.getId().equals(r.getId()))
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_PROVIDER_ID_REUSED", "Provider refund is already linked to another refund");
        if (r.getProviderRefundId() != null && !Objects.equals(r.getProviderRefundId(), providerRefundId))
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_PROVIDER_ID_MISMATCH", "A different provider refund is already linked to this refund");
        if (amountMinor != r.getAmountMinor()) throw new ApiException(HttpStatus.CONFLICT, "REFUND_EVENT_MISMATCH", "Provider refund amount does not match the local refund");
        String status = providerStatus == null ? "" : providerStatus;
        r.setProviderRefundId(providerRefundId);
        r.setProviderStatus(status);
        r.setLastAttemptAt(Instant.now());
        r.setAttemptCount(r.getAttemptCount() + 1);
        boolean done = status.equalsIgnoreCase("processed") || status.equalsIgnoreCase("completed") || status.equalsIgnoreCase("success") || status.equalsIgnoreCase("successful");
        if (!done) { r.setStatus(Enums.RefundStatus.PROCESSING); return; }
        r.setStatus(Enums.RefundStatus.COMPLETED);
        Payment p = payments.findByIdForUpdate(paymentId).orElseThrow();
        paymentStateMachine.transition(p,Enums.PaymentStatus.REFUNDED);
        orders.findById(p.getOrderId()).ifPresent(o -> o.setStatus(Enums.OrderStatus.CANCELLED));
        tickets.findByOrderIdOrderByTicketNumberAsc(p.getOrderId()).forEach(t -> { if (t.getStatus() != Enums.TicketStatus.CHECKED_IN) t.setStatus(Enums.TicketStatus.REFUNDED); });
        audit.log(null, "REFUND_COMPLETED", "PAYMENT", p.getPublicId().toString(), null);
    }

    public void processRefund(Long refundId){
        Refund pending=refunds.findById(refundId).orElse(null); if(pending==null||pending.getStatus()==Enums.RefundStatus.COMPLETED)return;
        Payment payment=payments.findById(pending.getPaymentId()).orElse(null); if(payment==null||payment.getProviderOrderId()==null)return;
        var lock=locks.tryAcquire("refund:"+payment.getId(),java.time.Duration.ofSeconds(90));if(!lock.acquired())return;try{finalizeFromProvider(pending.getId(),payment);}finally{lock.close();}
    }

    private void finalizeFromProvider(Long refundId,Payment payment){
        Refund pending=refunds.findById(refundId).orElse(null);if(pending==null||pending.getStatus()==Enums.RefundStatus.COMPLETED)return;
        try{
            PaymentGatewayProvider provider=gateways.forPayment(payment);
            var providerPayment=provider.fetchPaymentsForOrder(payment.getProviderOrderId()).stream()
                    .filter(x->x.amount()==payment.getAmountMinor()&&payment.getCurrency().equalsIgnoreCase(x.currency()))
                    .filter(x->"captured".equalsIgnoreCase(x.status()) && x.captured())
                    .findFirst().orElse(null);
            if(providerPayment==null||providerPayment.id()==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_PROVIDER_PENDING","Provider payment is not yet captured and available for refund");
            String receipt=pending.getProviderReceipt();String clean=pending.getPublicId().toString().replace("-","");String desired="LKRF-"+clean.substring(0,16);if(!desired.equals(receipt)){receipt=desired;final String rr=receipt;tx.executeWithoutResult(s->refunds.findById(refundId).ifPresent(x->x.setProviderReceipt(rr)));}
            String idempotency="LKREF-"+clean.substring(0,24); PaymentGatewayProvider.ProviderRefund result=null;
            final String refundReceipt=receipt;
            final long refundAmount=pending.getAmountMinor();
            var providerRefunds=(payment.getProvider()==Enums.PaymentProvider.CASHFREE
                    ? provider.fetchRefundsForOrder(payment.getProviderOrderId())
                    : provider.fetchRefundsForPayment(providerPayment.id()));
            var existing=providerRefunds.stream()
                    .filter(x->x.id()!=null && refundReceipt.equals(x.receipt()) && x.amount()==refundAmount)
                    .findFirst();
            if(existing.isPresent()) result=existing.get();
            else if(providerRefunds.stream().filter(x->refundReceipt.equals(x.receipt())).mapToLong(PaymentGatewayProvider.ProviderRefund::amount).sum()>0) {
                final String partialStatus="PARTIAL_PROVIDER_REFUND";
                tx.executeWithoutResult(status->{
                    Refund r=refunds.findById(refundId).orElseThrow();
                    r.setProviderStatus(partialStatus);
                    r.setLastError("A partial provider refund already exists for this refund; automatic re-refund is disabled to prevent over-refunding");
                    r.setLastAttemptAt(Instant.now());
                    r.setAttemptCount(r.getAttemptCount()+1);
                    // A partial provider refund requires human reconciliation; do not let the recovery worker
                    // repeatedly attempt another full refund. FAILED is retryable by an explicit admin request.
                    r.setStatus(Enums.RefundStatus.FAILED);
                });
                return;
            } else {
                result=provider.refund(providerPayment.id(),payment.getProviderOrderId(),pending.getAmountMinor(),pending.getReason(),receipt,idempotency);
            }
            final PaymentGatewayProvider.ProviderRefund finalResult=result;
            tx.executeWithoutResult(status->{Refund r=refunds.findById(refundId).orElseThrow();r.setProviderStatus(finalResult==null?"PENDING":finalResult.status());r.setProviderRefundId(finalResult==null?r.getProviderRefundId():finalResult.id());if(finalResult!=null&&finalResult.receipt()!=null)r.setProviderReceipt(finalResult.receipt());r.setLastAttemptAt(Instant.now());r.setAttemptCount(r.getAttemptCount()+1);String st=finalResult==null?"":finalResult.status();boolean done=st.equalsIgnoreCase("processed")||st.equalsIgnoreCase("completed")||st.equalsIgnoreCase("success")||st.equalsIgnoreCase("successful");if(done){r.setStatus(Enums.RefundStatus.COMPLETED);Payment p=payments.findById(r.getPaymentId()).orElseThrow();paymentStateMachine.transition(p,Enums.PaymentStatus.REFUNDED);orders.findById(p.getOrderId()).ifPresent(o->o.setStatus(Enums.OrderStatus.CANCELLED));tickets.findByOrderIdOrderByTicketNumberAsc(p.getOrderId()).forEach(t->{if(t.getStatus()!=Enums.TicketStatus.CHECKED_IN)t.setStatus(Enums.TicketStatus.REFUNDED);});audit.log(null,"REFUND_COMPLETED","PAYMENT",p.getPublicId().toString(),null);}else{r.setStatus(Enums.RefundStatus.PROCESSING);payments.findById(r.getPaymentId()).ifPresent(p->{paymentStateMachine.transition(p,Enums.PaymentStatus.REFUND_PENDING);});}});
        }catch(Exception ex){
            tx.executeWithoutResult(s->refunds.findById(refundId).ifPresent(r->{
                r.setLastError(safeError(ex));
                r.setLastAttemptAt(Instant.now());
                r.setAttemptCount(r.getAttemptCount()+1);
                boolean nonRetryable = ex instanceof ApiException api && (
                        "REFUND_PROVIDER_ID_REUSED".equals(api.code()) ||
                        "REFUND_PROVIDER_ID_MISMATCH".equals(api.code()) ||
                        "REFUND_EVENT_MISMATCH".equals(api.code()));
                r.setStatus(nonRetryable ? Enums.RefundStatus.FAILED : Enums.RefundStatus.PROCESSING);
            }));
        }
    }

    private String safeError(Throwable ex){
        String value=ex.getMessage();
        if(value==null || value.isBlank()) value=ex.getClass().getSimpleName();
        value=value.replaceAll("[\r\n\t]"," ");
        return value.length()>500?value.substring(0,500):value;
    }

    private boolean isFinancialRefundApprover(String role, Long organizerId, Long actorId) {
        if ("ADMIN".equalsIgnoreCase(role) || "FINANCE".equalsIgnoreCase(role)) return true;
        if (!"ORGANIZER".equalsIgnoreCase(role) || actorId == null) return false;
        return members.findByOrganizerIdAndUserId(organizerId, actorId)
                .map(m -> "OWNER".equalsIgnoreCase(m.getRole()) || "FINANCE".equalsIgnoreCase(m.getRole()))
                .orElse(false);
    }
    public record PreparedRefund(UUID paymentPublicId,Long refundId,UUID refundPublicId,long amountMinor,boolean alreadyQueued){}
    public record RefundResult(UUID paymentId,String refundId,long amountMinor,String status){}
}
