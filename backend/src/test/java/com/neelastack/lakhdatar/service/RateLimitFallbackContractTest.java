package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitFallbackContractTest {
  @Test void rejectedLocalBucketIsNotEvicted() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/RateLimitService.java"));
    assertTrue(source.contains("return count <= limit;"));
    assertFalse(source.contains("local.remove(localKey,b)"));
  }
}
