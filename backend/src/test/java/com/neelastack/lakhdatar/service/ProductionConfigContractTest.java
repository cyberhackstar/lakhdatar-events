package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigContractTest {
  @Test void productionGuardRequiresHttpsProviderAndSecureRefreshCookie() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/ProductionConfigurationGuard.java"));
    assertTrue(source.contains("requireHttps(\"RAZORPAY_BASE_URL\""));
    assertTrue(source.contains("AUTH_COOKIE_SECURE must be true in production"));
  }
}
