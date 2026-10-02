package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums; import com.neelastack.lakhdatar.exception.ApiException; import lombok.RequiredArgsConstructor; import org.springframework.core.env.Environment; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Component; import java.util.*;
@Component @RequiredArgsConstructor
public class PaymentGatewayRouter {
 private final List<PaymentGatewayProvider> providers; private final Environment environment;
 public PaymentGatewayProvider forProvider(Enums.PaymentProvider p){return providers.stream().filter(x->x.provider()==p).findFirst().orElseThrow(()->new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_PROVIDER_UNAVAILABLE","Selected payment provider is not configured"));}
 public PaymentGatewayProvider forPayment(com.neelastack.lakhdatar.domain.Payment p){return forProvider(p.getProvider());}
 public void requireConfigured(Enums.PaymentProvider p){ PaymentGatewayProvider provider=forProvider(p); boolean production=environment.matchesProfiles("prod","production") || "production".equalsIgnoreCase(environment.getProperty("APP_ENV")); if(production && !provider.isConfigured()) throw new ApiException(HttpStatus.CONFLICT,"PAYMENT_PROVIDER_NOT_CONFIGURED","The selected payment provider is not configured on the platform"); }
}
