package com.neelastack.lakhdatar.controller;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.service.AuthService;
import com.neelastack.lakhdatar.service.ClientAddressService;
import com.neelastack.lakhdatar.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
public class AuthMfaController {
    private static final String REFRESH_COOKIE = "lk_refresh";
    private final MfaService mfa;
    private final AuthService auth;
    private final ClientAddressService clientAddress;
    private final AppProperties props;

    public record EnrollmentBody(@NotBlank @Size(max=256) String challengeToken) {}
    public record VerifyBody(@NotBlank @Size(max=256) String challengeToken, @NotBlank @Size(max=6) String code) {}
    public record EnrollmentResponse(String secret, String otpauthUri, String qrDataUri) {}
    public record Response(String accessToken, String refreshToken, String tokenType, String role, String fullName) {}

    @PostMapping("/enroll")
    ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollmentBody body, HttpServletRequest req) {
        var r = mfa.beginEnrollment(body.challengeToken(), clientAddress.resolve(req));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EnrollmentResponse(r.secret(), r.otpauthUri(), r.qrDataUri()));
    }

    @PostMapping("/confirm")
    ResponseEntity<Response> confirm(@Valid @RequestBody VerifyBody body, HttpServletRequest req) {
        var u = mfa.confirmEnrollment(body.challengeToken(), body.code(), clientAddress.resolve(req));
        var r = auth.issueAfterMfa(u.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Set-Cookie", refreshCookie(r.refreshToken(), req))
                .body(new Response(r.accessToken(), "", "Bearer", r.role(), r.fullName()));
    }

    @PostMapping("/verify")
    ResponseEntity<Response> verify(@Valid @RequestBody VerifyBody body, HttpServletRequest req) {
        var u = mfa.verifyLogin(body.challengeToken(), body.code(), clientAddress.resolve(req));
        var r = auth.issueAfterMfa(u.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Set-Cookie", refreshCookie(r.refreshToken(), req))
                .body(new Response(r.accessToken(), "", "Bearer", r.role(), r.fullName()));
    }

    private String refreshCookie(String value, HttpServletRequest req) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true)
                .secure(props.security().refreshCookieSecure() || req.isSecure())
                .sameSite("Strict").path("/api/v1/auth")
                .maxAge(Math.max(60L, props.jwt().refreshToken().toSeconds())).build().toString();
    }
}
