package com.neelastack.lakhdatar.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAddressServiceTest {
    @Test
    void prefersCloudflareIpAndDoesNotResolveHostnamesFromForwardedHeader() {
        ClientAddressService service = new ClientAddressService();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.10");
        Mockito.when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.10");
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("203.0.113.10", service.resolve(request));

        HttpServletRequest noCf = Mockito.mock(HttpServletRequest.class);
        Mockito.when(noCf.getHeader("CF-Connecting-IP")).thenReturn(null);
        Mockito.when(noCf.getHeader("X-Real-IP")).thenReturn(null);
        Mockito.when(noCf.getHeader("X-Forwarded-For")).thenReturn("example.com");
        Mockito.when(noCf.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", service.resolve(noCf));
    }
}
