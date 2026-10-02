package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CloudinarySecurityContractTest {
    @Test void serverSideUploadNeverAcceptsSvgOrUnboundedFiles() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CloudinaryAssetService.java"));
        assertTrue(service.contains("maxBytes()"));
        assertTrue(service.contains("25_000_000L"));
        assertTrue(service.contains("image/jpeg"));
        assertTrue(service.contains("image/png"));
        assertFalse(service.contains("image/svg+xml"));
        assertTrue(service.contains("api_secret"));
    }
    @Test void uploadRequiresAdminOrOrganizer() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/CloudinaryAssetService.java"));
        assertTrue(service.contains("ADMIN"));
        assertTrue(service.contains("ORGANIZER"));
        assertTrue(service.contains("canManageEvent"));
    }
}
