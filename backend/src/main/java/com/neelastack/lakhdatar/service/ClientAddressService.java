package com.neelastack.lakhdatar.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
public class ClientAddressService {
    public String resolve(HttpServletRequest request) {
        String cf = first(request.getHeader("CF-Connecting-IP"));
        if (isValidIp(cf)) return cf;
        String real = first(request.getHeader("X-Real-IP"));
        if (isValidIp(real)) return real;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String forwardedClient = first(forwarded);
            if (isValidIp(forwardedClient)) return forwardedClient;
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String first(String value) {
        return value == null ? null : value.split(",")[0].trim();
    }


    private boolean isValidIp(String value) {
        if (value == null || value.isBlank() || value.length() > 64) return false;
        String v = value.trim();
        // Avoid DNS resolution on attacker-controlled proxy headers; accept only literal IP syntax.
        if (v.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            String[] octets = v.split("\\.");
            for (String octet : octets) {
                int n = Integer.parseInt(octet);
                if (n > 255) return false;
            }
            return true;
        }
        if (!v.matches("[0-9A-Fa-f:]+") || !v.contains(":")) return false;
        try {
            return InetAddress.getByName(v) instanceof java.net.Inet6Address;
        } catch (Exception ignored) {
            return false;
        }
    }
}
