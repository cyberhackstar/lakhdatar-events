package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for database-backed integration tests.
 *
 * <p>One PostgreSQL container is started once per JVM (singleton-container pattern) and every
 * subclass shares one cached Spring context. Previously each test class started its own container
 * and its own Spring context/Hikari pool, which roughly doubled memory use and was enough to
 * exhaust a small Docker Desktop VM.
 *
 * <p>Skipped automatically when Docker is unavailable. Run only the fast unit tests with
 * {@code mvn verify -Punit} (or {@code -DexcludedGroups=integration}).
 */
@Tag("integration")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
            POSTGRES.start(); // Ryuk removes the container when the JVM exits.
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) return;
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.bootstrap.enabled", () -> false);
        // Integration tests intentionally run without Redis. Use the bounded in-memory
        // fallback rather than waiting on the default Lettuce timeout or failing closed.
        // Production retains fail-closed behavior via application.yml.
        registry.add("spring.data.redis.timeout", () -> "300ms");
        registry.add("spring.data.redis.connect-timeout", () -> "300ms");
        registry.add("app.rate-limit.fail-closed-on-redis-error", () -> false);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 30);
    }
}
