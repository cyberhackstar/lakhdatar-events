package com.neelastack.lakhdatar.controller;
import com.neelastack.lakhdatar.service.ClientAddressService;
import com.neelastack.lakhdatar.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/public/checkout") @RequiredArgsConstructor
public class CheckoutController {
 private final OrderService orders; private final ClientAddressService clientAddress;
 public record Item(@NotNull UUID ticketTypeId,@Min(1) int quantity){}
 public record Body(@NotNull UUID eventId,@NotBlank @Size(max=120) String customerName,@NotBlank @Email @Size(max=255) String customerEmail,@Size(max=40) String customerPhone,@NotBlank @Size(min=16,max=100) String idempotencyKey,@NotEmpty @Size(max=20) List<@Valid Item> items){}
 public record Verify(@NotBlank @Size(max=80) String razorpayOrderId,@NotBlank @Size(max=80) String razorpayPaymentId,@NotBlank @Size(max=128) String razorpaySignature){}
 @PostMapping public ResponseEntity<OrderService.CheckoutResponse> checkout(@Valid @RequestBody Body b,HttpServletRequest req){var r=orders.checkout(new OrderService.CheckoutRequest(b.eventId(),b.customerName(),b.customerEmail(),b.customerPhone(),b.idempotencyKey(),b.items().stream().map(i->new OrderService.CheckoutItem(i.ticketTypeId(),i.quantity())).toList(),clientAddress.resolve(req)));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r);}
 @PostMapping("/verify") public ResponseEntity<OrderService.VerifyResponse> verify(@Valid @RequestBody Verify b){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(orders.verifyAndConfirm(new OrderService.VerifyRequest(b.razorpayOrderId(),b.razorpayPaymentId(),b.razorpaySignature())));}
}
