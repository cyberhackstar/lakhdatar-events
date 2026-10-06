package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Protects application capacity from a slow or unavailable external payment provider.
 * The guard is deliberately dependency-light: local bulkheads protect each JVM while
 * provider idempotency/reconciliation protect correctness across the HA cluster.
 */
@Component
public final class PaymentProviderGuard {
    private final AppProperties.Payment config;
    private final java.util.Map<Enums.PaymentProvider, State> states = new java.util.EnumMap<>(Enums.PaymentProvider.class);

    public PaymentProviderGuard(AppProperties props) {
        this.config = props.payment();
        for (Enums.PaymentProvider provider : Enums.PaymentProvider.values()) {
            states.put(provider, new State(
                    Math.max(1, config.maxConcurrent()),
                    Math.max(1, config.failureThreshold()),
                    Math.max(1, config.bulkheadAcquireTimeoutMs()),
                    Math.max(1, config.circuitOpenSeconds())));
        }
    }

    public PaymentGatewayProvider wrap(PaymentGatewayProvider delegate) {
        State state = states.get(delegate.provider());
        return new GuardedProvider(delegate, state);
    }

    private final class GuardedProvider implements PaymentGatewayProvider {
        private final PaymentGatewayProvider delegate;
        private final State state;

        private GuardedProvider(PaymentGatewayProvider delegate, State state) {
            this.delegate = delegate;
            this.state = state;
        }

        @Override public Enums.PaymentProvider provider() { return delegate.provider(); }
        @Override public boolean isConfigured() { return delegate.isConfigured(); }

        @Override public ProviderOrder createOrder(long amount, String currency, String receipt, String customerName, String customerEmail, String customerPhone) {
            return state.call("create-order", () -> delegate.createOrder(amount, currency, receipt, customerName, customerEmail, customerPhone));
        }
        @Override public ProviderOrder fetchOrder(String orderId) { return state.call("fetch-order", () -> delegate.fetchOrder(orderId)); }
        @Override public Optional<ProviderOrder> findOrderByReceipt(String receipt) { return state.call("find-order", () -> delegate.findOrderByReceipt(receipt)); }
        @Override public List<ProviderPayment> fetchPaymentsForOrder(String orderId) { return state.call("fetch-payments", () -> delegate.fetchPaymentsForOrder(orderId)); }
        @Override public ProviderPayment fetchPayment(String paymentId) { return state.call("fetch-payment", () -> delegate.fetchPayment(paymentId)); }
        @Override public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) { return delegate.verifyPaymentSignature(orderId, paymentId, signature); }
        @Override public boolean verifyWebhookSignature(String rawBody, String signature, String timestamp) { return delegate.verifyWebhookSignature(rawBody, signature, timestamp); }
        @Override public ProviderRefund refund(String paymentId, String orderId, long amount, String reason, String receipt, String idempotencyKey) {
            return state.call("refund", () -> delegate.refund(paymentId, orderId, amount, reason, receipt, idempotencyKey));
        }
        @Override public List<ProviderRefund> fetchRefundsForPayment(String paymentId) { return state.call("fetch-refunds", () -> delegate.fetchRefundsForPayment(paymentId)); }
        @Override public List<ProviderRefund> fetchRefundsForOrder(String orderId) { return state.call("fetch-order-refunds", () -> delegate.fetchRefundsForOrder(orderId)); }
        @Override public Optional<ProviderRefund> fetchRefund(String paymentId, String refundId) { return state.call("fetch-refund", () -> delegate.fetchRefund(paymentId, refundId)); }
    }

    private static final class State {
        private final Semaphore permits;
        private final int failureThreshold;
        private final long acquireTimeoutMs;
        private final long circuitOpenSeconds;
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private volatile long openUntilNanos;
        private boolean probeInFlight;

        private State(int maxConcurrent, int failureThreshold, long acquireTimeoutMs, long circuitOpenSeconds) {
            this.permits = new Semaphore(maxConcurrent, true);
            this.failureThreshold = failureThreshold;
            this.acquireTimeoutMs = acquireTimeoutMs;
            this.circuitOpenSeconds = circuitOpenSeconds;
        }

        private <T> T call(String operation, java.util.function.Supplier<T> action) {
            long now = System.nanoTime();
            if (now < openUntilNanos) {
                throw unavailable("PAYMENT_PROVIDER_CIRCUIT_OPEN", "Payment provider is temporarily unavailable");
            }
            synchronized (this) {
                if (openUntilNanos > System.nanoTime()) {
                    throw unavailable("PAYMENT_PROVIDER_CIRCUIT_OPEN", "Payment provider is temporarily unavailable");
                }
                if (openUntilNanos != 0 && probeInFlight) {
                    throw unavailable("PAYMENT_PROVIDER_CIRCUIT_OPEN", "Payment provider is recovering; retry shortly");
                }
                if (openUntilNanos != 0) probeInFlight = true;
            }

            boolean acquired = false;
            try {
                acquired = permits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw unavailable("PAYMENT_PROVIDER_BUSY", "Payment provider capacity is temporarily exhausted");
                }
                T result = action.get();
                success();
                return result;
            } catch (RuntimeException ex) {
                if (isProviderFailure(ex)) failure();
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure();
                throw unavailable("PAYMENT_PROVIDER_BUSY", "Payment provider capacity is temporarily exhausted");
            } finally {
                if (acquired) permits.release();
                synchronized (this) { if (openUntilNanos != 0) probeInFlight = false; }
            }
        }

        private boolean isProviderFailure(RuntimeException ex) {
            if (ex instanceof ApiException api) {
                return api.status() == HttpStatus.BAD_GATEWAY || api.status() == HttpStatus.GATEWAY_TIMEOUT || api.status() == HttpStatus.SERVICE_UNAVAILABLE;
            }
            return true;
        }

        private void success() {
            synchronized (this) {
                consecutiveFailures.set(0);
                openUntilNanos = 0;
                probeInFlight = false;
            }
        }

        private void failure() {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold) {
                synchronized (this) {
                    openUntilNanos = System.nanoTime() + Duration.ofSeconds(circuitOpenSeconds).toNanos();
                    probeInFlight = false;
                }
            }
        }

        private ApiException unavailable(String code, String message) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
        }
    }
}
