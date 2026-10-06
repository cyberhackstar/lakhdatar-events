package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class AppPropertiesBindingTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(TestPropertiesConfiguration.class)
            .withPropertyValues(
                    "app.jwt.secret=12345678901234567890123456789012",
                    "app.jwt.access-token=15m",
                    "app.jwt.refresh-token=14d",
                    "app.jwt.issuer=neelastack-events-test",
                    "app.jwt.audience=neelastack-events-test-web",
                    "app.qr.signing-secret=12345678901234567890123456789012",
                    "app.qr.image-size=512",
                    "app.security.ticket-view-secret=12345678901234567890123456789012",
                    "app.security.refresh-cookie-secure=false",
                    "app.security.ticket-view-ttl=24h",
                    "app.checkout.max-tickets-per-order=20",
                    "app.checkout.session-ttl=20m",
                    "app.rate-limit.login-per-window=10",
                    "app.rate-limit.public-checkout-per-window=20",
                    "app.rate-limit.scan-per-minute=240",
                    "app.rate-limit.window-seconds=60",
                    "app.rate-limit.fail-closed-on-redis-error=true",
                    "app.payment.reconciliation-age-ms=120000",
                    "app.payment.reconciliation-sweep-ms=30000",
                    "app.payment.max-concurrent=32",
                    "app.payment.failure-threshold=5",
                    "app.payment.bulkhead-acquire-timeout-ms=1000",
                    "app.payment.circuit-open-seconds=15"
            );

    @Test
    void nestedRecordsWithConvenienceConstructorsAreBound() {
        context.run(ctx -> {
            AppProperties props = ctx.getBean(AppProperties.class);
            assertNotNull(props.jwt());
            assertEquals("12345678901234567890123456789012", props.jwt().secret());
            assertEquals("neelastack-events-test", props.jwt().issuer());
            assertEquals("neelastack-events-test-web", props.jwt().audience());
            assertNotNull(props.qr());
            assertEquals(512, props.qr().imageSize());
            assertNotNull(props.security());
            assertNotNull(props.checkout());
            assertNotNull(props.rateLimit());
            assertNotNull(props.payment());
            assertEquals(32, props.payment().maxConcurrent());
            assertEquals(5, props.payment().failureThreshold());
            assertEquals(1000, props.payment().bulkheadAcquireTimeoutMs());
            assertEquals(15, props.payment().circuitOpenSeconds());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class TestPropertiesConfiguration {}
}
