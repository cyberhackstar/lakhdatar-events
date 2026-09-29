package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketViewTtlContractTest {
  @Test void ticketViewUsesDedicatedTtl() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AccessTokenService.java"));
    assertTrue(source.contains("props.security().ticketViewTtl()"));
  }
}
