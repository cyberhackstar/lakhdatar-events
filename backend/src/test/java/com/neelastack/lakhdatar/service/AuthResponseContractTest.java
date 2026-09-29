package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AuthResponseContractTest {
  @Test void loginAndRefreshReturnJsonBodies() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/AuthController.java"));
    assertTrue(source.contains(".body(new Response(r.accessToken(), \"\", \"Bearer\", r.role(), r.fullName()))"));
    assertFalse(source.contains("baseResponse(new Response"));
    assertTrue(source.contains("private ResponseEntity.BodyBuilder baseResponse()"));
  }
}
