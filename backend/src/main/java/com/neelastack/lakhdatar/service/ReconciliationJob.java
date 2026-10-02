package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Payment;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component @RequiredArgsConstructor
public class ReconciliationJob {
    private static final Logger log=LoggerFactory.getLogger(ReconciliationJob.class);
    private final PaymentRepository payments; private final PaymentGatewayRouter gateways; private final OrderService orders; private final AppProperties props;
    @Scheduled(fixedDelayString="${app.payment.reconciliation-sweep-ms:30000}")
    public void reconcile(){
        Instant cutoff=Instant.now().minusMillis(props.payment().reconciliationAgeMs());
        List<Enums.PaymentStatus> states=List.of(Enums.PaymentStatus.CREATED,Enums.PaymentStatus.PENDING,Enums.PaymentStatus.PAYMENT_INITIATED,Enums.PaymentStatus.AUTHORIZED,Enums.PaymentStatus.CAPTURED);
        for(Payment p:payments.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(states,cutoff)){
            if(p.getProviderOrderId()==null) continue;
            try{
                var provider=gateways.forPayment(p).fetchPaymentsForOrder(p.getProviderOrderId()).stream()
                        .filter(x->"captured".equalsIgnoreCase(x.status())||"refunded".equalsIgnoreCase(x.status()))
                        .filter(x->x.amount()==p.getAmountMinor() && p.getCurrency().equalsIgnoreCase(x.currency()))
                        .findFirst();
                if(provider.isPresent()){
                    if("refunded".equalsIgnoreCase(provider.get().status())) orders.markProviderRefunded(p.getId(),provider.get());
                    else {var result=orders.reconcileCapturedPayment(p.getId(),provider.get()); if("REFUND_PENDING".equals(result.status())) orders.completeQueuedRefundIfNeeded(p.getId(),"Reservation expired before payment reconciliation");}
                }
            }catch(Exception ex){log.warn("Payment reconciliation deferred paymentId={}",p.getId());}
        }
        for(Payment p:payments.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List.of(Enums.PaymentStatus.REFUND_PENDING),cutoff)){
            try{orders.completeQueuedRefundIfNeeded(p.getId(),"Payment captured without an active ticket reservation");}catch(Exception ex){log.warn("Refund reconciliation deferred paymentId={}",p.getId());}
        }
    }
}
