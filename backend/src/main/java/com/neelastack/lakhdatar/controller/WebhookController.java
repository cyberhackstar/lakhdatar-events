package com.neelastack.lakhdatar.controller;
import com.neelastack.lakhdatar.service.WebhookService; import lombok.RequiredArgsConstructor; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/webhooks") @RequiredArgsConstructor
public class WebhookController { private final WebhookService webhooks; @PostMapping("/razorpay") ResponseEntity<Void> razorpay(@RequestBody String body,@RequestHeader(value="X-Razorpay-Signature",required=false) String signature,@RequestHeader(value="X-Razorpay-Event-Id",required=false) String eventId){webhooks.handle(body,signature,eventId);return ResponseEntity.ok().build();} }
