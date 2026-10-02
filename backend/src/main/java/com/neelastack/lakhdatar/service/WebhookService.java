package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.domain.PaymentWebhookEvent;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.PaymentRepository;
import com.neelastack.lakhdatar.repository.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service @RequiredArgsConstructor
public class WebhookService {
 private final RazorpayService razorpay; private final PaymentGatewayRouter gateways; private final PaymentWebhookEventRepository events; private final PaymentRepository payments; private final OrderService orders; private final TransactionTemplate tx; private final ObjectMapper mapper=new ObjectMapper();
 public void handle(String raw,String signature,String headerEventId){
  if(signature==null||!razorpay.verifyWebhookSignature(raw,signature)) throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_WEBHOOK","Webhook signature invalid");
  JsonNode n; try{n=mapper.readTree(raw);}catch(Exception ex){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WEBHOOK_BODY","Webhook payload is invalid");}
  String headerId=normalizeEventId(headerEventId);
  String payloadId=normalizeEventId(n.path("id").asText(null));
  if(headerId!=null && payloadId!=null && !headerId.equals(payloadId)) {
   throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WEBHOOK_ID","Webhook event identifiers do not match");
  }
  String providerEventId=headerId!=null?headerId:payloadId; if(providerEventId==null) providerEventId=sha256(raw);
  final String eventId=providerEventId; persistIfAbsent(eventId,n.path("event").asText("unknown"),raw,sha256(raw));
  PaymentWebhookEvent stored=events.findByProviderEventId(eventId).orElseThrow(()->new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"WEBHOOK_PERSISTENCE_FAILED","Webhook could not be persisted"));
  if(stored.isProcessed()) return;
  if(claim(eventId)==0) return;
  try{process(stored.getPayload()); tx.executeWithoutResult(s->events.markProcessed(eventId,Instant.now()));}
  catch(RuntimeException ex){tx.executeWithoutResult(s->events.markFailed(eventId,safeError(ex))); throw ex;}
 }
 private void persistIfAbsent(String eventId,String type,String raw,String hash){
  try{tx.executeWithoutResult(s->{if(events.findByProviderEventId(eventId).isEmpty()){PaymentWebhookEvent e=new PaymentWebhookEvent();e.setProviderEventId(eventId);e.setEventType(type);e.setPayload(raw);e.setPayloadHash(hash);events.saveAndFlush(e);}});}catch(org.springframework.dao.DataIntegrityViolationException ignored){}
 }
 private int claim(String id){Integer r=tx.execute(s->events.claimForProcessing(id,Instant.now()));return r==null?0:r;}
 private void process(String payload){
  JsonNode event;try{event=mapper.readTree(payload);}catch(Exception ex){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WEBHOOK_BODY","Stored webhook payload is invalid");}
  String type=event.path("event").asText(""); JsonNode pe=event.path("payload").path("payment").path("entity"); String orderId=pe.path("order_id").asText(null); String paymentId=pe.path("id").asText(null);
  if(("payment.captured".equals(type) || "payment.authorized".equals(type))&&orderId!=null&&paymentId!=null){
   var p=payments.findByRazorpayOrderId(orderId).orElseThrow(()->new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_NOT_LINKED","Payment event arrived before the local payment was linked"));
   long amount=pe.path("amount").asLong(-1); String currency=pe.path("currency").asText(null);
   if(p.getAmountMinor()!=amount||!p.getCurrency().equalsIgnoreCase(currency)) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_EVENT_MISMATCH","Payment webhook does not match the local order");
   var provider=gateways.forProvider(com.neelastack.lakhdatar.domain.Enums.PaymentProvider.RAZORPAY).fetchPayment(paymentId);
   if("refunded".equalsIgnoreCase(provider.status())) orders.markProviderRefunded(p.getId(),provider);
   else if("captured".equalsIgnoreCase(provider.status())){var result=orders.reconcileCapturedPayment(p.getId(),provider);if("REFUND_PENDING".equals(result.status()))orders.completeQueuedRefundIfNeeded(p.getId(),"Reservation expired, event closed, or order no longer payable before payment capture");}
   else if("authorized".equalsIgnoreCase(provider.status())) {
    // Re-read under a row lock: the payment may have been captured/refunded since it was loaded above,
    // and a stale save must never regress a terminal status back to AUTHORIZED.
    final Long localPaymentId=p.getId();
    tx.executeWithoutResult(s->payments.findByIdForUpdate(localPaymentId).ifPresent(locked->{
     var st=locked.getStatus();
     if(st==com.neelastack.lakhdatar.domain.Enums.PaymentStatus.CREATED||st==com.neelastack.lakhdatar.domain.Enums.PaymentStatus.PENDING||st==com.neelastack.lakhdatar.domain.Enums.PaymentStatus.PAYMENT_INITIATED){
      orders.transitionPaymentForWebhook(locked, com.neelastack.lakhdatar.domain.Enums.PaymentStatus.AUTHORIZED);
      locked.setRazorpayPaymentId(paymentId);
      locked.setProviderLastError(null);
      payments.save(locked);
     }
    }));
   }
   else throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_STATE_PENDING","Provider has not reached a terminal captured/refunded state");
  } else if("payment.failed".equals(type)&&orderId!=null){
   if(payments.findByRazorpayOrderId(orderId).isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_NOT_LINKED","Payment failed event arrived before the local payment was linked");
   orders.failPayment(orderId,pe.path("error_description").asText("Payment failed"));
  }
 }
 @Scheduled(fixedDelayString="${app.razorpay.webhook-recovery-sweep:60000}") void recoverStaleProcessing(){int reset=events.resetStaleProcessing(Instant.now().minus(Duration.ofMinutes(10)));if(reset>0)org.slf4j.LoggerFactory.getLogger(WebhookService.class).warn("Reset {} stale webhook-processing claims",reset);}
 private String normalizeEventId(String v){if(v==null)return null;String s=v.trim();return s.matches("[A-Za-z0-9._:-]{8,150}")?s:null;}
 private String safeError(Throwable ex){String s=ex.getMessage();if(s==null||s.isBlank())s=ex.getClass().getSimpleName();s=s.replaceAll("[\r\n\t]"," ");return s.length()>500?s.substring(0,500):s;}
 private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
