package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import java.util.List;
import java.util.Optional;

public interface PaymentGatewayProvider {
    Enums.PaymentProvider provider();
    default boolean isConfigured() { return true; }
    record ProviderOrder(String id, String publicKey, String sessionId, long amount, String currency, String receipt, String status) {}
    record ProviderPayment(String id, String orderId, long amount, String currency, String status, String errorDescription, long amountRefunded, String refundStatus, boolean captured) {}
    record ProviderRefund(String id, String status, long amount, String receipt) {}
    ProviderOrder createOrder(long amount, String currency, String receipt, String customerName, String customerEmail, String customerPhone);
    ProviderOrder fetchOrder(String orderId);
    Optional<ProviderOrder> findOrderByReceipt(String receipt);
    List<ProviderPayment> fetchPaymentsForOrder(String orderId);
    ProviderPayment fetchPayment(String paymentId);
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);
    boolean verifyWebhookSignature(String rawBody, String signature, String timestamp);
    ProviderRefund refund(String paymentId, String orderId, long amount, String reason, String receipt, String idempotencyKey);
    List<ProviderRefund> fetchRefundsForPayment(String paymentId);
    default List<ProviderRefund> fetchRefundsForOrder(String orderId) { return List.of(); }
    Optional<ProviderRefund> fetchRefund(String paymentId, String refundId);
}
