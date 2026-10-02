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
                    "app.rate-limit.fail-closed-on-redis-error=true"
            );

    @Test
    void nestedRecordsWithConvenienceConstructorsAreBound() {
        context.run(ctx -> {
            AppProperties props = ctx.getBean(AppProperties.class);
            assertNotNull(props.qr());
            assertEquals(512, props.qr().imageSize());
            assertNotNull(props.security());
            assertNotNull(props.checkout());
            assertNotNull(props.rateLimit());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class TestPropertiesConfiguration {}
}
