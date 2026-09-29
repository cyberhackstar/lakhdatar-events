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
        assertTrue(controller.contains("new Response(r.accessToken(), \"\", \"Bearer\", r.role(), r.fullName())"));
        assertTrue(controller.contains("props.security().refreshCookieSecure()"));
        assertTrue(controller.contains("props.jwt().refreshToken().toSeconds()"));
    }
}
