package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List; import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RazorpayGatewayProvider implements PaymentGatewayProvider {
    private final RazorpayService delegate; private final AppProperties props;
    public Enums.PaymentProvider provider(){return Enums.PaymentProvider.RAZORPAY;}
 public boolean isConfigured(){return props.razorpay().keyId()!=null&&!props.razorpay().keyId().isBlank()&&props.razorpay().keySecret()!=null&&!props.razorpay().keySecret().isBlank()&&props.razorpay().webhookSecret()!=null&&!props.razorpay().webhookSecret().isBlank();}
    public ProviderOrder createOrder(long a,String c,String r,String n,String e,String ph){var x=delegate.createOrder(a,c,r);return new ProviderOrder(x.razorpayOrderId(),x.keyId(),null,a,c,r,"created");}
    public ProviderOrder fetchOrder(String id){var x=delegate.fetchOrder(id);return new ProviderOrder(x.id(),props.razorpay().keyId(),null,x.amount(),x.currency(),x.receipt(),x.status());}
    public Optional<ProviderOrder> findOrderByReceipt(String r){return delegate.findOrderByReceipt(r).map(x->new ProviderOrder(x.id(),props.razorpay().keyId(),null,x.amount(),x.currency(),x.receipt(),x.status()));}
    public List<ProviderPayment> fetchPaymentsForOrder(String id){return delegate.fetchPaymentsForOrder(id).stream().map(this::map).toList();}
    public ProviderPayment fetchPayment(String id){return map(delegate.fetchPayment(id));}
    private ProviderPayment map(RazorpayService.ProviderPayment x){return new ProviderPayment(x.id(),x.orderId(),x.amount(),x.currency(),x.status(),x.errorDescription(),x.amountRefunded(),x.refundStatus(),x.captured());}
    public boolean verifyPaymentSignature(String o,String p,String s){return delegate.verifyPaymentSignature(o,p,s);}
    public boolean verifyWebhookSignature(String raw,String sig,String ts){return delegate.verifyWebhookSignature(raw,sig);}
    public ProviderRefund refund(String p,String o,long a,String reason,String receipt,String key){var x=delegate.refund(p,a,reason,receipt,key);return new ProviderRefund(x.refundId(),x.status(),x.amount(),receipt);}
    public List<ProviderRefund> fetchRefundsForPayment(String p){return delegate.fetchRefundsForPayment(p).stream().map(x->new ProviderRefund(x.id(),x.status(),x.amount(),x.receipt())).toList();}
    public Optional<ProviderRefund> fetchRefund(String p,String r){return delegate.fetchRefund(p,r).map(x->new ProviderRefund(x.id(),x.status(),x.amount(),x.receipt()));}
}
