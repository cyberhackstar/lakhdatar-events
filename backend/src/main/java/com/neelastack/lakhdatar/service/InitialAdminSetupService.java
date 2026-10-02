package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.Enums;
import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.domain.OrganizerMember;
import com.neelastack.lakhdatar.domain.PlatformSetupState;
import com.neelastack.lakhdatar.domain.User;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.OrganizerMemberRepository;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import com.neelastack.lakhdatar.repository.PlatformSetupStateRepository;
import com.neelastack.lakhdatar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InitialAdminSetupService {
    private static final short STATE_ID = 1;
    private final AppProperties props;
    private final PlatformSetupStateRepository stateRepository;
    private final UserRepository users;
    private final OrganizerRepository organizers;
    private final OrganizerMemberRepository members;
    private final PasswordEncoder passwordEncoder;

    public record SetupRequest(String email, String password, String name, String organizerName, String organizerSlug) {}
    public record SetupStatus(boolean enabled, boolean completed) {}
    public record SetupResult(String email, String organizerSlug) {}

    @Transactional(readOnly = true)
    public SetupStatus status() {
        PlatformSetupState state = stateRepository.findById(STATE_ID).orElse(null);
        return new SetupStatus(props.initialAdmin().enabled(), state != null && state.getInitialAdminCompletedAt() != null);
    }

    @Transactional
    public SetupResult create(String presentedToken, SetupRequest request) {
        if (!props.initialAdmin().enabled()) throw new ApiException(HttpStatus.NOT_FOUND, "SETUP_DISABLED", "Initial administrator setup is disabled");
        if (!constantTimeEquals(props.initialAdmin().setupToken(), presentedToken)) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SETUP_TOKEN", "Setup authorization is invalid");
        validate(request);

        PlatformSetupState state = stateRepository.findByIdForUpdate(STATE_ID)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SETUP_UNAVAILABLE", "Initial setup state is unavailable"));
        if (state.getInitialAdminCompletedAt() != null || users.existsByRole(Enums.UserRole.ADMIN))
            throw new ApiException(HttpStatus.CONFLICT, "SETUP_ALREADY_COMPLETED", "Initial administrator setup has already been completed");

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(email).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "An account already exists for this email address");

        String slug = request.organizerSlug().trim().toLowerCase(Locale.ROOT);
        if (organizers.findBySlug(slug).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "ORGANIZER_EXISTS", "An organizer with this slug already exists");

        User admin = new User();
        admin.setEmail(email);
        admin.setFullName(request.name().trim());
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setRole(Enums.UserRole.ADMIN);
        admin.setEnabled(true);
        admin = users.saveAndFlush(admin);

        Organizer organizer = new Organizer();
        organizer.setName(request.organizerName().trim());
        organizer.setSlug(slug);
        organizer.setDescription("Organizer on the Neelastack Events platform.");
        organizer = organizers.saveAndFlush(organizer);

        OrganizerMember member = new OrganizerMember();
        member.setOrganizerId(organizer.getId());
        member.setUserId(admin.getId());
        member.setRole("OWNER");
        members.save(member);

        state.setInitialAdminCompletedAt(Instant.now());
        stateRepository.save(state);
        return new SetupResult(admin.getEmail(), organizer.getSlug());
    }

    private void validate(SetupRequest r) {
        if (r == null || r.email() == null || !r.email().matches("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$") || r.email().length() > 255)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter a valid administrator email");
        if (r.password() == null || r.password().length() < 12 || r.password().length() > 128)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Administrator password must be 12 to 128 characters");
        if (r.name() == null || r.name().trim().length() < 2 || r.name().trim().length() > 120)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Administrator name is invalid");
        if (r.organizerName() == null || r.organizerName().trim().length() < 2 || r.organizerName().trim().length() > 255)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZER", "Organizer name is invalid");
        if (r.organizerSlug() == null || !r.organizerSlug().matches("[a-z0-9]+(?:-[a-z0-9]+){0,80}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZER_SLUG", "Use a lowercase URL-safe organizer slug");
    }

    private boolean constantTimeEquals(String expected, String presented) {
        if (expected == null || presented == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
