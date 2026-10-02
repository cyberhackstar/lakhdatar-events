package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.service.CashfreeWebhookService;
import com.neelastack.lakhdatar.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookService webhooks;
    private final CashfreeWebhookService cashfree;

    @PostMapping("/razorpay")
    ResponseEntity<Void> razorpay(
            @RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        webhooks.handle(body, signature, eventId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cashfree")
    ResponseEntity<Void> cashfree(
            @RequestBody String body,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature,
            @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp) {
        cashfree.handle(body, signature, timestamp);
        return ResponseEntity.ok().build();
    }
}
