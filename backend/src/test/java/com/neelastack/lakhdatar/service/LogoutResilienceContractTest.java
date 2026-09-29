package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LogoutResilienceContractTest {
  @Test void clientMarksExplicitLogoutToAvoidCookieResurrection() throws Exception {
    Path frontend = Path.of("../frontend/src/app/core/auth/auth.service.ts");
    if (!Files.exists(frontend)) frontend = Path.of("frontend/src/app/core/auth/auth.service.ts");
    String source = Files.readString(frontend);
    assertTrue(source.contains("const LOGGED_OUT = 'lk_logged_out';"));
    assertTrue(source.contains("markExplicitLogout()"));
    assertTrue(source.contains("wasExplicitlyLoggedOut()"));
  }
}
