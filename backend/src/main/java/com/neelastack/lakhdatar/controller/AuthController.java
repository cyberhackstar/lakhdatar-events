package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.service.AuthService;
import com.neelastack.lakhdatar.service.ClientAddressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String REFRESH_COOKIE = "lk_refresh";
    private final AuthService auth;
    private final ClientAddressService clientAddress;
    private final AppProperties props;

    public record LoginBody(@NotBlank @Email @Size(max=255) String email, @NotBlank @Size(max=128) String password) {}
    /** Body refresh is retained as a non-browser API fallback; browsers use the HttpOnly cookie. */
    public record RefreshBody(@Size(max=512) String refreshToken) {}
    public record LogoutBody(@Size(max=512) String refreshToken) {}
    public record Response(String accessToken, String refreshToken, String tokenType, String role, String fullName) {}

    @PostMapping("/login")
    ResponseEntity<Response> login(@Valid @RequestBody LoginBody b, HttpServletRequest req) {
        var r = auth.login(b.email(), b.password(), clientAddress.resolve(req));
        return baseResponse()
                .header("Set-Cookie", refreshCookie(r.refreshToken(), req))
                .body(new Response(r.accessToken(), "", "Bearer", r.role(), r.fullName()));
    }

    @PostMapping("/refresh")
    ResponseEntity<Response> refresh(@RequestBody(required = false) RefreshBody b, HttpServletRequest req) {
        String raw = cookieValue(req, REFRESH_COOKIE);
        if (raw == null || raw.isBlank()) raw = b == null ? null : b.refreshToken();
        var r = auth.refresh(raw);
        return baseResponse()
                .header("Set-Cookie", refreshCookie(r.refreshToken(), req))
                .body(new Response(r.accessToken(), "", "Bearer", r.role(), r.fullName()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestBody(required = false) LogoutBody b, HttpServletRequest req) {
        String raw = cookieValue(req, REFRESH_COOKIE);
        if (raw == null || raw.isBlank()) raw = b == null ? null : b.refreshToken();
        auth.revoke(raw);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header("Set-Cookie", expiredRefreshCookie(req))
                .build();
    }

    private ResponseEntity.BodyBuilder baseResponse() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Cache-Control", "no-store, no-cache, must-revalidate");
    }

    private String refreshCookie(String value, HttpServletRequest req) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.security().refreshCookieSecure() || req.isSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(Math.max(60L, props.jwt().refreshToken().toSeconds()))
                .build().toString();
    }

    private String expiredRefreshCookie(HttpServletRequest req) {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(props.security().refreshCookieSecure() || req.isSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build().toString();
    }

    private String cookieValue(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return null;
        for (var c : req.getCookies()) if (name.equals(c.getName())) return c.getValue();
        return null;
    }
}
