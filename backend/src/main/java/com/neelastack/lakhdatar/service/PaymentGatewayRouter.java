package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.EnterpriseLog;

import com.neelastack.lakhdatar.domain.Enums; import com.neelastack.lakhdatar.exception.ApiException; import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory; import org.springframework.core.env.Environment; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Component; import java.util.*;
@Component @RequiredArgsConstructor
public class PaymentGatewayRouter {
    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayRouter.class);
 private final List<PaymentGatewayProvider> providers; private final Environment environment;
 public PaymentGatewayProvider forProvider(Enums.PaymentProvider p){
  PaymentGatewayProvider provider=providers.stream().filter(x->x.provider()==p).findFirst().orElse(null);
  if(provider==null){ EnterpriseLog.error(log, "payment.provider.unavailable", null, "event.category", "payment", "provider", p==null?"UNKNOWN":p.name()); throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_PROVIDER_UNAVAILABLE","Selected payment provider is not configured"); }
  return provider;
 }
 public PaymentGatewayProvider forPayment(com.neelastack.lakhdatar.domain.Payment p){return forProvider(p.getProvider());}
 public Enums.PaymentProvider defaultProvider(){
  EnterpriseLog.debug(log, "payment.provider.default.resolve", "event.category", "payment");
  String raw=environment.getProperty("DEFAULT_PAYMENT_PROVIDER", "CASHFREE");
  try { return Enums.PaymentProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
  catch (RuntimeException ex) { EnterpriseLog.error(log, "payment.provider.configuration.invalid", ex, "event.category", "payment"); throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"PAYMENT_PROVIDER_CONFIGURATION_INVALID","DEFAULT_PAYMENT_PROVIDER must be RAZORPAY or CASHFREE"); }
 }
 public void requireConfigured(Enums.PaymentProvider p){ PaymentGatewayProvider provider=forProvider(p); boolean production=environment.matchesProfiles("prod","production") || "production".equalsIgnoreCase(environment.getProperty("APP_ENV")); if(production && !provider.isConfigured()){ EnterpriseLog.error(log, "payment.provider.configuration.missing", null, "event.category", "payment", "provider", p==null?"UNKNOWN":p.name()); throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_NOT_CONFIGURED","The selected payment provider is not configured on the platform"); } }
}
