package com.neelastack.lakhdatar.service;

import com.cloudinary.Cloudinary;
import com.neelastack.lakhdatar.config.AppProperties;
import com.neelastack.lakhdatar.domain.BrandConfiguration;
import com.neelastack.lakhdatar.domain.Event;
import com.neelastack.lakhdatar.domain.Organizer;
import com.neelastack.lakhdatar.exception.ApiException;
import com.neelastack.lakhdatar.repository.BrandConfigurationRepository;
import com.neelastack.lakhdatar.repository.EventRepository;
import com.neelastack.lakhdatar.repository.OrganizerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class CloudinaryAssetService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryAssetService.class);
    private final AppProperties props;
    private final EventRepository events;
    private final OrganizerRepository organizers;
    private final BrandConfigurationRepository brands;
    private final EventAccessService eventAccess;
    private final TransactionTemplate tx;

    public enum Purpose { ORGANIZER_LOGO, EVENT_LOGO, EVENT_BANNER, EVENT_COVER }
    public record UploadResult(String secureUrl, String publicId, Purpose purpose) {}

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UploadResult upload(MultipartFile file, Purpose purpose, UUID eventPublicId, String organizerSlug, Long actorId, String role) {
        validateConfiguration();
        if (!"ADMIN".equals(role) && !"ORGANIZER".equals(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only administrators and organizer owners can manage branding assets");
        byte[] bytes = validatedImageBytes(file);
        Target target = resolveTarget(purpose, eventPublicId, organizerSlug, actorId, role);
        // Upload to the remote provider before opening a DB transaction. External network latency
        // must never hold a PostgreSQL connection/lock. The DB mutation is then atomic, with one
        // best-effort remote compensation path if the transaction rolls back.
        UploadResult result = uploadBytes(bytes, purpose);
        AtomicBoolean cleaned = registerRollbackCompensation(result.publicId());
        try {
            tx.executeWithoutResult(status -> apply(target, result));
            // The DB commit is complete here. Delete the superseded provider asset; if cleanup fails,
            // the new DB reference remains correct and the old asset can be reclaimed operationally.
            if (target.previousPublicId() != null && !target.previousPublicId().equals(result.publicId())) {
                deleteRemote(target.previousPublicId());
            }
            return result;
        } catch (RuntimeException ex) {
            if (cleaned.compareAndSet(false, true)) deleteRemote(result.publicId());
            throw ex;
        }
    }

    /**
     * Validates and uploads an image to Cloudinary without attaching it to any existing record.
     * Used when an organizer is created: the caller stores the returned secure URL on the new organizer.
     * This method intentionally performs only the provider upload; callers must atomically persist the
     * returned URL and compensate it if that DB transaction fails. Callers are responsible for authorization.
     */
    public UploadResult storeImage(MultipartFile file, Purpose purpose) {
        validateConfiguration();
        return uploadBytes(validatedImageBytes(file), purpose);
    }

    /** Whether Cloudinary credentials are present, so the UI can explain why a logo cannot be uploaded. */
    public boolean isConfigured() {
        return notBlank(props.cloudinary().cloudName()) && notBlank(props.cloudinary().apiKey()) && notBlank(props.cloudinary().apiSecret());
    }

    private byte[] validatedImageBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Choose an image to upload");
        if (file.getSize() > props.cloudinary().maxBytes()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Image exceeds the configured upload limit");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!SetOfImages.ALLOWED.contains(contentType)) throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE", "Only JPEG and PNG images are supported");

        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_READ_FAILED", "The image could not be read"); }
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            long pixels = image == null ? 0L : (long) image.getWidth() * image.getHeight();
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 8000 || image.getHeight() > 8000 || pixels > 25_000_000L)
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image is invalid or has unsupported dimensions");
        } catch (IOException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image is invalid"); }
        return bytes;
    }

    private UploadResult uploadBytes(byte[] bytes, Purpose purpose) {
        String publicId = props.cloudinary().folder().replaceAll("[^A-Za-z0-9/_-]", "_") + "/" + purpose.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID();
        try {
            Cloudinary cloudinary = new Cloudinary(Map.of(
                    "cloud_name", props.cloudinary().cloudName(),
                    "api_key", props.cloudinary().apiKey(),
                    "api_secret", props.cloudinary().apiSecret(),
                    "secure", true));
            Map<?, ?> result = cloudinary.uploader().upload(bytes, Map.of(
                    "resource_type", "image",
                    "public_id", publicId,
                    "overwrite", false,
                    "unique_filename", false,
                    "invalidate", true,
                    "quality", "auto",
                    "fetch_format", "auto"));
            String secureUrl = String.valueOf(result.get("secure_url"));
            if (secureUrl.isBlank() || !secureUrl.startsWith("https://")) throw new IllegalStateException("Cloudinary returned an invalid secure URL");
            return new UploadResult(secureUrl, String.valueOf(result.get("public_id")), purpose);
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_PROVIDER_ERROR", "Image storage is temporarily unavailable"); }
    }

    private static boolean notBlank(String v) { return v != null && !v.isBlank(); }

    private Target resolveTarget(Purpose purpose, UUID eventPublicId, String organizerSlug, Long actorId, String role) {
        if (purpose == Purpose.ORGANIZER_LOGO) {
            if (organizerSlug == null || organizerSlug.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "ORGANIZER_REQUIRED", "Organizer is required for an organizer logo");
            Organizer organizer = organizers.findBySlug(organizerSlug.trim().toLowerCase(Locale.ROOT)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZER_NOT_FOUND", "Organizer not found"));
            if (!eventAccess.canManage(actorId, role, organizer.getId())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this organizer");
            return new Target(purpose, organizer, null, null, organizer.getLogoPublicId());
        }
        if (eventPublicId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "EVENT_REQUIRED", "Event is required for this branding asset");
        Event event = events.findByPublicId(eventPublicId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found"));
        if (!eventAccess.canManageEvent(event.getId(), actorId, role)) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorized for this event");
        BrandConfiguration brand = event.getBrandConfigId() == null ? null : brands.findById(event.getBrandConfigId()).orElse(null);
        if (brand == null) throw new ApiException(HttpStatus.CONFLICT, "BRANDING_NOT_CONFIGURED", "Event branding is not configured");
        String previous = switch (purpose) {
            case EVENT_LOGO -> brand.getEventLogoPublicId();
            case EVENT_BANNER -> brand.getEventBannerPublicId();
            case EVENT_COVER -> event.getCoverImagePublicId();
            default -> null;
        };
        return new Target(purpose, null, event, brand, previous);
    }

    private AtomicBoolean registerRollbackCompensation(String publicId) {
        AtomicBoolean cleaned = new AtomicBoolean(false);
        if (publicId == null || publicId.isBlank() || !TransactionSynchronizationManager.isSynchronizationActive()) return cleaned;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK && cleaned.compareAndSet(false, true)) {
                    deleteRemote(publicId);
                }
            }
        });
        return cleaned;
    }

    /** Best-effort remote compensation. Cloudinary destroy is idempotent for a missing public id. */
    public void deleteRemote(String publicId) {
        if (publicId == null || publicId.isBlank() || !isConfigured()) return;
        try {
            Cloudinary cloudinary = new Cloudinary(Map.of(
                    "cloud_name", props.cloudinary().cloudName(),
                    "api_key", props.cloudinary().apiKey(),
                    "api_secret", props.cloudinary().apiSecret(),
                    "secure", true));
            cloudinary.uploader().destroy(publicId, Map.of("resource_type", "image", "invalidate", true));
        } catch (Exception ex) {
            log.warn("Cloudinary compensation failed for uploaded asset (reason={})", ex.getClass().getSimpleName());
        }
    }

    private void apply(Target target, UploadResult upload) {
        String url = upload.secureUrl();
        String publicId = upload.publicId();
        if (target.purpose == Purpose.ORGANIZER_LOGO) {
            target.organizer.setLogoUrl(url);
            target.organizer.setLogoPublicId(publicId);
            organizers.save(target.organizer);
            for (BrandConfiguration b : brands.findByOrganizerId(target.organizer.getId())) {
                b.setOrganizerLogoUrl(url);
                b.setOrganizerLogoPublicId(publicId);
                brands.save(b);
            }
            return;
        }
        switch (target.purpose) {
            case EVENT_LOGO -> { target.brand.setEventLogoUrl(url); target.brand.setEventLogoPublicId(publicId); }
            case EVENT_BANNER -> { target.brand.setEventBannerUrl(url); target.brand.setEventBannerPublicId(publicId); }
            case EVENT_COVER -> { target.event.setCoverImageUrl(url); target.event.setCoverImagePublicId(publicId); }
            default -> throw new IllegalStateException("Unsupported asset purpose");
        }
        brands.save(target.brand);
        if (target.event != null) events.save(target.event);
    }

    private void validateConfiguration() {
        if (props.cloudinary().cloudName() == null || props.cloudinary().cloudName().isBlank()
                || props.cloudinary().apiKey() == null || props.cloudinary().apiKey().isBlank()
                || props.cloudinary().apiSecret() == null || props.cloudinary().apiSecret().isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_STORAGE_UNAVAILABLE", "Cloudinary media storage is not configured");
    }

    private record Target(Purpose purpose, Organizer organizer, Event event, BrandConfiguration brand, String previousPublicId) {}
    private static final class SetOfImages { static final java.util.Set<String> ALLOWED = java.util.Set.of("image/jpeg", "image/png"); }
}
