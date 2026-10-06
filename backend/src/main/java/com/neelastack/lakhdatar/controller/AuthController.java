package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.service.AuthService;
import com.neelastack.lakhdatar.service.ClientAddressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import com.neelastack.lakhdatar.security.UserPrincipal;
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
    public record AcceptInviteBody(@NotBlank @Size(max=256) String token, @NotBlank @Size(min=12,max=128) String password) {}
    public record ChangePasswordBody(@NotBlank @Size(max=128) String currentPassword, @NotBlank @Size(min=12,max=128) String newPassword) {}
    public record PasswordStatus(boolean mustChangePassword) {}
    public record Response(String accessToken, String refreshToken, String tokenType, String role, String fullName,
                           boolean mfaRequired, boolean mfaSetupRequired, String mfaChallengeToken) {
        public Response(String accessToken, String refreshToken, String tokenType, String role, String fullName) {
            this(accessToken, refreshToken, tokenType, role, fullName, false, false, null);
        }
    }

    @PostMapping("/login")
    ResponseEntity<Response> login(@Valid @RequestBody LoginBody b, HttpServletRequest req) {
        var r = auth.login(b.email(), b.password(), clientAddress.resolve(req));
        return authResponse(r, req).body(toResponse(r));
    }

    /** Completes an emailed invite: sets the password, consumes the one-time token and signs the user in. */
    @PostMapping("/accept-invite")
    ResponseEntity<Response> acceptInvite(@Valid @RequestBody AcceptInviteBody b, HttpServletRequest req) {
        var r = auth.acceptInvite(b.token(), b.password(), clientAddress.resolve(req));
        return authResponse(r, req).body(toResponse(r));
    }

    /** Authenticated. Required before any other API call while must_change_password is set (enforced in JwtAuthFilter). */
    @PostMapping("/change-password")
    ResponseEntity<Response> changePassword(@Valid @RequestBody ChangePasswordBody b, Authentication a, HttpServletRequest req) {
        UserPrincipal u = (UserPrincipal) a.getPrincipal();
        var r = auth.changePassword(u.userId(), b.currentPassword(), b.newPassword(), u.mfaVerified());
        return authResponse(r, req).body(toResponse(r));
    }

    @GetMapping("/password-status")
    ResponseEntity<PasswordStatus> passwordStatus(Authentication a) {
        UserPrincipal u = (UserPrincipal) a.getPrincipal();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PasswordStatus(auth.mustChangePassword(u.userId())));
    }

    @PostMapping("/refresh")
    ResponseEntity<Response> refresh(@RequestBody(required = false) RefreshBody b, HttpServletRequest req) {
        String raw = cookieValue(req, REFRESH_COOKIE);
        if (raw == null || raw.isBlank()) raw = b == null ? null : b.refreshToken();
        var r = auth.refresh(raw);
        return authResponse(r, req).body(toResponse(r));
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

    private ResponseEntity.BodyBuilder authResponse(AuthService.AuthResult r, HttpServletRequest req) {
        var builder = baseResponse();
        if (r.refreshToken() != null && !r.refreshToken().isBlank()) {
            builder.header("Set-Cookie", refreshCookie(r.refreshToken(), req));
        } else if (r.mfaRequired()) {
            // A pre-existing browser session must not survive a fresh privileged login challenge.
            // Clear the old refresh cookie while the one-time MFA challenge is completed.
            builder.header("Set-Cookie", expiredRefreshCookie(req));
        }
        return builder;
    }

    private Response toResponse(AuthService.AuthResult r) {
        return new Response(r.accessToken(), "", "Bearer", r.role(), r.fullName(), r.mfaRequired(), r.mfaSetupRequired(), r.mfaChallengeToken());
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
