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

  @Test void productionRequiresSelectedPaymentProviderAndRealSupportContacts() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/ProductionConfigurationGuard.java"));
    assertTrue(source.contains("DEFAULT_PAYMENT_PROVIDER must be CASHFREE or RAZORPAY in production"));
    assertTrue(source.contains("DEFAULT_PAYMENT_PROVIDER=CASHFREE but Cashfree live credentials are not configured in production"));
    assertTrue(source.contains("DEFAULT_PAYMENT_PROVIDER=RAZORPAY but Razorpay live credentials are not configured in production"));
    assertTrue(source.contains("SUPPORT_EMAIL must be a real, verified support address in production"));
    assertTrue(source.contains("SUPPORT_PHONE must be a real phone number with 8-15 digits, not a placeholder, in production"));
    assertTrue(source.contains("supportPhoneDigits.length() < 8"));
    assertTrue(source.contains("@your-domain.com"));
  }
}
