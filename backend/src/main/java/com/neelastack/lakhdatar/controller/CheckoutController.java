package com.neelastack.lakhdatar.controller;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.service.ClientAddressService;
import com.neelastack.lakhdatar.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/public/checkout") @RequiredArgsConstructor
public class CheckoutController {
 private final OrderService orders; private final ClientAddressService clientAddress; private final AppProperties props;
 public record Item(@NotNull UUID ticketTypeId,@Min(1) int quantity){}
 public record Body(@NotNull UUID eventId,@NotBlank @Size(max=120) String customerName,@NotBlank @Email @Size(max=255) String customerEmail,@Size(max=40) String customerPhone,@NotBlank @Size(min=16,max=100) String idempotencyKey,@NotEmpty @Size(max=20) List<@Valid Item> items){}
 public record Verify(@NotBlank @Size(max=255) String providerOrderId,@Size(max=255) String providerPaymentId,@Size(max=512) String providerSignature){}
 @PostMapping public ResponseEntity<OrderService.CheckoutResponse> checkout(@Valid @RequestBody Body b,HttpServletRequest req){
  var r=orders.checkout(new OrderService.CheckoutRequest(b.eventId(),b.customerName(),b.customerEmail(),b.customerPhone(),b.idempotencyKey(),b.items().stream().map(i->new OrderService.CheckoutItem(i.ticketTypeId(),i.quantity())).toList(),clientAddress.resolve(req)));
  var out=ResponseEntity.ok().cacheControl(CacheControl.noStore());
  if(r.checkoutSessionToken()!=null){
    ResponseCookie cookie=ResponseCookie.from("ld_checkout",r.checkoutSessionToken()).httpOnly(true).secure(props.security().refreshCookieSecure()).sameSite("Lax").path("/api/v1/public/checkout").maxAge(r.checkoutSessionTtlSeconds()).build();
    out.header("Set-Cookie",cookie.toString());
  }
  return out.body(r);
 }
 @PostMapping("/verify") public ResponseEntity<OrderService.VerifyResponse> verify(@Valid @RequestBody Verify b,@CookieValue(name="ld_checkout",required=false) String checkoutSessionToken,HttpServletRequest req){
  OrderService.VerifyResponse response=orders.verifyAndConfirm(new OrderService.VerifyRequest(b.providerOrderId(),b.providerPaymentId(),b.providerSignature(),checkoutSessionToken,clientAddress.resolve(req)));
  ResponseEntity.BodyBuilder out=ResponseEntity.ok().cacheControl(CacheControl.noStore());
  if ("CONFIRMED".equalsIgnoreCase(response.status()) || "REFUND_PENDING".equalsIgnoreCase(response.status())) {
    ResponseCookie clear=ResponseCookie.from("ld_checkout","").httpOnly(true).secure(props.security().refreshCookieSecure()).sameSite("Lax").path("/api/v1/public/checkout").maxAge(0).build();
    out.header("Set-Cookie",clear.toString());
  }
  return out.body(response);
 }
}
