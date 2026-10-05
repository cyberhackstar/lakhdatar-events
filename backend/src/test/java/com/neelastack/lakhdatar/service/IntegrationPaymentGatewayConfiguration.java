package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Enums;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic payment gateway used only by database-backed integration tests.
 *
 * <p>The production application still requires a real configured provider before an event can be
 * published. Keeping this provider in test scope prevents integration tests from depending on
 * merchant credentials or a live payment service.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationPaymentGatewayConfiguration {
    @Bean
    @Order(-1000)
    PaymentGatewayProvider integrationRazorpayGateway() {
        return new PaymentGatewayProvider() {
            @Override public Enums.PaymentProvider provider() { return Enums.PaymentProvider.RAZORPAY; }
            @Override public boolean isConfigured() { return true; }

            @Override
            public ProviderOrder createOrder(long amount, String currency, String receipt,
                                             String customerName, String customerEmail, String customerPhone) {
                return new ProviderOrder("test-order-" + receipt, "test-key", null, amount, currency, receipt, "created");
            }

            @Override public ProviderOrder fetchOrder(String orderId) {
                return new ProviderOrder(orderId, "test-key", null, 0, "INR", orderId, "created");
            }

            @Override public Optional<ProviderOrder> findOrderByReceipt(String receipt) {
                return Optional.empty();
            }

            @Override public List<ProviderPayment> fetchPaymentsForOrder(String orderId) {
                return List.of();
            }

            @Override public ProviderPayment fetchPayment(String paymentId) {
                throw new UnsupportedOperationException("Direct payment lookup is not used by this integration fixture");
            }

            @Override public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
                return false;
            }

            @Override public boolean verifyWebhookSignature(String rawBody, String signature, String timestamp) {
                return false;
            }

            @Override
            public ProviderRefund refund(String paymentId, String orderId, long amount, String reason,
                                         String receipt, String idempotencyKey) {
                return new ProviderRefund("test-refund-" + receipt, "processed", amount, receipt);
            }

            @Override public List<ProviderRefund> fetchRefundsForPayment(String paymentId) {
                return List.of();
            }

            @Override public Optional<ProviderRefund> fetchRefund(String paymentId, String refundId) {
                return Optional.empty();
            }
        };
    }
}
