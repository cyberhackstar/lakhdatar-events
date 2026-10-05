package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AuthCookieContractTest {
    @Test
    void refreshCredentialIsHttpOnlyCookieAndNotJsonResponse() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/AuthController.java"));
        assertTrue(controller.contains("REFRESH_COOKIE"));
        assertTrue(controller.contains("httpOnly(true)"));
        assertTrue(controller.contains("sameSite(\"Strict\")"));
        assertTrue(controller.contains("private Response toResponse(AuthService.AuthResult r)"));
        assertTrue(controller.contains("r.mfaRequired()"));
        assertTrue(controller.contains("props.security().refreshCookieSecure()"));
        assertTrue(controller.contains("props.jwt().refreshToken().toSeconds()"));
    }
}
