package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UploadErrorHandlingContractTest {
  @Test void oversizedUploadsAreReportedAsPayloadTooLarge() throws Exception {
    String source = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/exception/GlobalExceptionHandler.java"));
    assertTrue(source.contains("MaxUploadSizeExceededException"));
    assertTrue(source.contains("PAYLOAD_TOO_LARGE"));
    assertTrue(source.contains("IMAGE_TOO_LARGE"));
  }
}
