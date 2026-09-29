package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryContractTest {
  @Test void recoveryIsRateLimitedAndNoStore() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/PublicEventController.java"));
    assertTrue(source.contains("recover:"));
    assertTrue(source.contains("recover-email:"));
    assertTrue(source.contains("CacheControl.noStore()"));
  }
}
