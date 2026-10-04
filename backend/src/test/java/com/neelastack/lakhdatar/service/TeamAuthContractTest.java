package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.domain.UserInvite;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.RefreshTokenRepository;
import com.neelastack.lakhdatar.repository.UserInviteRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import com.neelastack.lakhdatar.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deactivated users cannot sign in or scan; invites are single-use and expire; initial passwords must be changed. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamAuthContractTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock UserInviteRepository invites;
    @Mock JwtService jwt;
    @Mock RateLimitService rateLimits;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) AppProperties props;
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    AuthService auth;

    @BeforeEach void setUp() {
        auth = new AuthService(users, refreshTokens, invites, encoder, jwt, rateLimits, props);
        when(rateLimits.allow(anyString(), anyInt(), any())).thenReturn(true);
        when(props.rateLimit().windowSeconds()).thenReturn(60);
        when(props.rateLimit().loginPerWindow()).thenReturn(10);
        when(props.jwt().refreshToken()).thenReturn(Duration.ofDays(14));
    }

    private User staff(boolean enabled) {
        User u = new User(); u.setId(7L); u.setEmail("gate@example.com"); u.setFullName("Gate"); u.setRole(Enums.UserRole.STAFF);
        u.setEnabled(enabled); u.setPasswordHash(encoder.encode("correct-horse-battery")); return u;
    }

    @Test void deactivatedStaffCannotSignInEvenWithTheRightPassword() {
        when(users.findByEmailIgnoreCase("gate@example.com")).thenReturn(Optional.of(staff(false)));
        ApiException e = assertThrows(ApiException.class, () -> auth.login("gate@example.com", "correct-horse-battery", "ip"));
        assertEquals("INVALID_CREDENTIALS", e.code());
        verify(refreshTokens, never()).save(any());
    }

    @Test void deactivatedStaffCannotRefreshASession() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AuthService.java"));
        assertTrue(src.contains("if(!u.isEnabled()) throw new ApiException(HttpStatus.UNAUTHORIZED,\"ACCOUNT_DISABLED\""));
    }

    @Test void everyApiRequestIsRejectedForDisabledUsersSoScanningStopsImmediately() throws Exception {
        String f = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/JwtAuthFilter.java"));
        assertTrue(f.contains("filter(com.neelastack.lakhdatar.domain.User::isEnabled)"), "disabled users must never be authenticated");
        String sec = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/SecurityConfig.java"));
        assertTrue(sec.contains("\"/api/v1/checkin/**\",\"/api/v1/staff/**\").hasAnyRole("), "scan endpoints require an authenticated role");
    }

    @Test void initialPasswordAccountsAreBlockedUntilTheyChangeIt() throws Exception {
        String f = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/security/JwtAuthFilter.java"));
        assertTrue(f.contains("PASSWORD_CHANGE_REQUIRED"));
        assertTrue(f.contains("!req.getRequestURI().startsWith(\"/api/v1/auth/\")"));
        String sec = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/config/SecurityConfig.java"));
        assertTrue(sec.contains("\"/api/v1/auth/change-password\",\"/api/v1/auth/password-status\").authenticated()"));
    }

    @Test void acceptingAnInviteSetsThePasswordClearsTheFlagAndConsumesTheToken() {
        User u = staff(true); u.setMustChangePassword(true);
        UserInvite inv = new UserInvite(); inv.setUserId(7L); inv.setTokenHash(TeamService.sha256("tok")); inv.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        when(invites.findByTokenHashForUpdate(TeamService.sha256("tok"))).thenReturn(Optional.of(inv));
        when(users.findById(7L)).thenReturn(Optional.of(u));

        auth.acceptInvite("tok", "brand-new-password", "ip");

        assertFalse(u.isMustChangePassword());
        assertTrue(encoder.matches("brand-new-password", u.getPasswordHash()));
        assertNotNull(inv.getUsedAt());
        verify(refreshTokens).revokeAllActiveByUserId(eq(7L), any());
    }

    @Test void inviteLinksAreSingleUse() {
        User u = staff(true);
        UserInvite inv = new UserInvite(); inv.setUserId(7L); inv.setExpiresAt(Instant.now().plus(Duration.ofHours(1))); inv.setUsedAt(Instant.now());
        when(invites.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(inv));
        when(users.findById(7L)).thenReturn(Optional.of(u));
        assertEquals("INVALID_INVITE", assertThrows(ApiException.class, () -> auth.acceptInvite("tok", "brand-new-password", "ip")).code());
    }

    @Test void expiredInvitesAreRejected() {
        UserInvite inv = new UserInvite(); inv.setUserId(7L); inv.setExpiresAt(Instant.now().minusSeconds(1));
        when(invites.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(inv));
        when(users.findById(7L)).thenReturn(Optional.of(staff(true)));
        assertEquals("INVALID_INVITE", assertThrows(ApiException.class, () -> auth.acceptInvite("tok", "brand-new-password", "ip")).code());
    }

    @Test void invitesForDeactivatedUsersAndUnknownTokensGiveTheSameError() {
        UserInvite inv = new UserInvite(); inv.setUserId(7L); inv.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        when(invites.findByTokenHashForUpdate(TeamService.sha256("known"))).thenReturn(Optional.of(inv));
        when(users.findById(7L)).thenReturn(Optional.of(staff(false)));
        assertEquals("INVALID_INVITE", assertThrows(ApiException.class, () -> auth.acceptInvite("known", "brand-new-password", "ip")).code());
        when(invites.findByTokenHashForUpdate(TeamService.sha256("unknown"))).thenReturn(Optional.empty());
        assertEquals("INVALID_INVITE", assertThrows(ApiException.class, () -> auth.acceptInvite("unknown", "brand-new-password", "ip")).code());
    }

    @Test void changePasswordRequiresTheCurrentPasswordAndClearsTheFlag() {
        User u = staff(true); u.setMustChangePassword(true);
        when(users.findById(7L)).thenReturn(Optional.of(u));
        assertEquals("INVALID_CURRENT_PASSWORD", assertThrows(ApiException.class, () -> auth.changePassword(7L, "wrong", "another-long-password")).code());
        assertTrue(u.isMustChangePassword());
        auth.changePassword(7L, "correct-horse-battery", "another-long-password");
        assertFalse(u.isMustChangePassword());
        assertTrue(encoder.matches("another-long-password", u.getPasswordHash()));
    }

    @Test void legacyAdminOnlyEndpointsRemainAdminOnlyAndDeprecated() throws Exception {
        String svc = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/service/AdminService.java"));
        assertTrue(svc.contains("Only an administrator can provision staff users"));
        assertTrue(svc.contains("Only an administrator can provision event managers"));
        String ctl = Files.readString(Path.of("src/main/java/com/neelastack/lakhdatar/controller/AdminController.java"));
        assertTrue(ctl.contains("@Deprecated\n    @PostMapping(\"/staff\")"));
        assertTrue(ctl.contains("@Deprecated\n    @PostMapping(\"/managers\")"));
    }

    @Test void migrationEnforcesOneOrganizerPerTeamMember() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V19__organizer_scoped_team.sql"));
        assertTrue(sql.contains("must_change_password"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_organizer_member_single_team_org"));
        assertTrue(sql.contains("WHERE role IN ('STAFF', 'EVENT_MANAGER')"));
        assertTrue(sql.contains("token_hash"), "only a hash of the invite token is stored");
    }
}
