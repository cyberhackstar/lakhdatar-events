package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.util.regex.Pattern;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Release-contract guards for enterprise SRE/production qualification controls. */
class ProductionHardeningV206ContractTest {
    @Test
    void teamAuthTestUsesBehavioralFilterCoverage() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/neelastack/lakhdatar/service/TeamAuthContractTest.java"));
        assertTrue(source.contains("MockHttpServletRequest"));
        assertTrue(source.contains("PASSWORD_CHANGE_REQUIRED"));
        assertFalse(source.contains("!req.getRequestURI().startsWith(\"/api/v1/auth/\")"),
                "forced-password-change test must not depend on implementation formatting");
    }

    @Test
    void multiEventIntegrationTestUsesOnlyATestPaymentProvider() throws Exception {
        String integrationSource = Files.readString(
                Path.of("src/test/java/com/neelastack/lakhdatar/service/IntegrationPaymentGatewayConfiguration.java"));
        String multiEventSource = Files.readString(
                Path.of("src/test/java/com/neelastack/lakhdatar/service/MultiEventCatalogIntegrationTest.java"));

        assertTrue(integrationSource.contains("@TestConfiguration"));
        assertTrue(integrationSource.contains("integrationRazorpayGateway"));
        assertTrue(integrationSource.contains("@Order(-1000)"));
        assertTrue(integrationSource.contains("Enums.PaymentProvider.RAZORPAY"));
        assertTrue(integrationSource.contains("isConfigured() { return true; }"));

        assertTrue(multiEventSource.contains("@Import(IntegrationPaymentGatewayConfiguration.class)"));
        assertTrue(multiEventSource.contains("e.setPaymentProvider(Enums.PaymentProvider.RAZORPAY)"));
    }

    @Test
    void releaseVersionMatchesAllBuildManifests() throws Exception {
        String version = Files.readString(Path.of("../VERSION")).trim();
        String pom = Files.readString(Path.of("pom.xml"));
        String pkg = Files.readString(Path.of("../frontend/package.json"));
        assertTrue(Pattern.matches("\\d+\\.\\d+\\.\\d+", version));
        assertTrue(pom.contains("<artifactId>lakhdatar-events</artifactId>"));
        assertTrue(pom.contains("<version>" + version + "</version>"));
        assertTrue(pkg.contains("\"version\": \"" + version + "\""));
    }
    @Test
    void concurrencyAndObservabilityGuardsArePresent() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(config.contains("max-connections: ${SERVER_TOMCAT_MAX_CONNECTIONS:10000}"));
        assertTrue(config.contains("accept-count: ${SERVER_TOMCAT_ACCEPT_COUNT:1000}"));
        assertTrue(config.contains("max: ${SERVER_TOMCAT_MAX_THREADS:200}"));
        assertTrue(config.contains("keep-alive-timeout: ${SERVER_TOMCAT_KEEP_ALIVE_TIMEOUT:30s}"));
        assertTrue(config.contains("enabled: ${OTEL_METRICS_EXPORT_ENABLED:false}"));
        assertTrue(config.contains("OPS_LOGS_ENABLED"));
        assertTrue(config.contains("LOKI_URL"));
    }

    @Test
    void edgeAndLockfileProductionContractsArePresent() throws Exception {
        String edge = Files.readString(Path.of("../edge/nginx.conf"));
        String lock = Files.readString(Path.of("../frontend/package-lock.json"));
        assertTrue(edge.contains("return 302 /monitor$is_args$args;"));
        assertTrue(edge.contains("location = /monitor"));
        assertTrue(edge.contains("if ($is_monitor_host = 0) { return 404; }"));
        assertTrue(edge.contains("proxy_cache_path /var/cache/nginx/public-cache"));
        assertTrue(edge.contains("keepalive 64;"));
        assertTrue(edge.contains("gzip on;"));
        assertTrue(edge.contains("zone=public_catalog_api:10m rate=1200r/s"));
        assertTrue(edge.contains("limit_conn per_ip 1000;"));
        assertTrue(edge.contains("$cookie_lk_refresh"));
        assertTrue(edge.contains("$cookie_ld_checkout"));
        assertTrue(lock.contains("\"node_modules/void-elements\": {\n      \"version\": \"2.0.1\""));
        assertTrue(lock.contains("\"node_modules/http-errors\": {\n      \"version\": \"2.0.1\""));
        assertTrue(lock.contains("\"node_modules/inherits\": {\n      \"version\": \"2.0.4\""));
        assertFalse(lock.contains("inherits-2.0.5.tgz"));
        assertTrue(edge.contains("location ^~ /api/v1/admin/ops/"));
        assertTrue(edge.contains("location = /api/v1/admin/ops/logs"));
        assertTrue(edge.contains("if ($is_monitor_host = 1) { return 404; }"));
        assertFalse(lock.contains("void-elements-2.0.2.tgz"));
        assertFalse(lock.contains("http-errors-2.0.2.tgz"));
        String version = Files.readString(Path.of("../VERSION")).trim();
        String manifest = Files.readString(Path.of("../RELEASE-MANIFEST.txt"));
        assertTrue(manifest.contains("Release: " + version));
        assertTrue(Files.isRegularFile(Path.of("src/main/java/com/neelastack/lakhdatar/service/OperationsLogService.java")));
    }

}
