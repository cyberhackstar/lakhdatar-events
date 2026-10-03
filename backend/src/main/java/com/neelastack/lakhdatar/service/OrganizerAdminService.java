package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Organizer (event company) management.
 *
 * An organizer's name and logo are entered by a platform administrator when the organizer is created.
 * The logo is uploaded to Cloudinary and only the returned HTTPS URL is stored; nothing about an
 * organizer's identity comes from environment variables or files shipped inside the application.
 */
@Service
@RequiredArgsConstructor
public class OrganizerAdminService {
    private final OrganizerRepository organizers;
    private final EventAccessService eventAccess;
    private final CloudinaryAssetService assets;
    private final AuditService audit;

    public record OrganizerSummary(UUID id, String slug, String name, String logoUrl, String description, String website) {}
    public record OrganizerList(boolean mediaStorageConfigured, List<OrganizerSummary> organizers) {}

    @Transactional(readOnly = true)
    public OrganizerList list(Long actorId, String role) {
        List<OrganizerSummary> out = organizers.findAll().stream()
                .filter(o -> eventAccess.canManage(actorId, role, o.getId()))
                .sorted(Comparator.comparing(Organizer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::summary)
                .toList();
        return new OrganizerList(assets.isConfigured(), out);
    }

    /**
     * Creates an organizer. The Cloudinary upload happens before the database write and outside any
     * database transaction, so a failed upload leaves no half-created organizer and no connection is
     * held open during the network call.
     */
    public OrganizerSummary create(String name, String slug, String description, String website, MultipartFile logo, Long actorId, String role) {
        if (!"ADMIN".equals(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only platform administrators can create organizers");

        String cleanName = name == null ? "" : name.trim();
        if (cleanName.length() < 2 || cleanName.length() > 255)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZER", "Organizer name must be between 2 and 255 characters");
        String cleanSlug = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        if (!cleanSlug.matches("[a-z0-9]+(?:-[a-z0-9]+){0,80}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZER_SLUG", "Use a lowercase URL-safe organizer slug");
        String cleanDescription = description == null || description.isBlank() ? null : description.trim();
        if (cleanDescription != null && cleanDescription.length() > 5000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZER", "Organizer description is too long");
        String cleanWebsite = website == null || website.isBlank() ? null : website.trim();
        if (cleanWebsite != null && (cleanWebsite.length() > 255 || !cleanWebsite.matches("https://[^\\s\"'<>]+")))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZER", "Organizer website must be a valid https:// URL");
        if (organizers.findBySlug(cleanSlug).isPresent())
            throw new ApiException(HttpStatus.CONFLICT, "ORGANIZER_EXISTS", "An organizer with this slug already exists");

        String logoUrl = null;
        if (logo != null && !logo.isEmpty())
            logoUrl = assets.storeImage(logo, CloudinaryAssetService.Purpose.ORGANIZER_LOGO).secureUrl();

        Organizer o = new Organizer();
        o.setName(cleanName);
        o.setSlug(cleanSlug);
        o.setDescription(cleanDescription);
        o.setWebsite(cleanWebsite);
        o.setLogoUrl(logoUrl);
        try {
            o = organizers.saveAndFlush(o);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ORGANIZER_EXISTS", "An organizer with this slug already exists");
        }
        audit.log(actorId, "ORGANIZER_CREATED", "ORGANIZER", o.getPublicId().toString(), null);
        return summary(o);
    }

    private OrganizerSummary summary(Organizer o) {
        return new OrganizerSummary(o.getPublicId(), o.getSlug(), o.getName(), o.getLogoUrl(), o.getDescription(), o.getWebsite());
    }
}
