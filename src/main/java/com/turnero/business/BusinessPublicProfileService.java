package com.turnero.business;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import com.turnero.storage.ObjectStorageService;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BusinessPublicProfileService {

    private static final long LOGO_MAX_BYTES = 1024L * 1024L;
    private static final long COVER_MAX_BYTES = 3L * 1024L * 1024L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final BusinessRepository businessRepository;
    private final OwnershipGuard ownershipGuard;
    private final ObjectStorageService storageService;

    public BusinessPublicProfileService(
            BusinessRepository businessRepository,
            OwnershipGuard ownershipGuard,
            ObjectStorageService storageService
    ) {
        this.businessRepository = businessRepository;
        this.ownershipGuard = ownershipGuard;
        this.storageService = storageService;
    }

    @Transactional
    public BusinessPublicProfileResponse update(
            UUID businessId,
            BusinessPublicProfileRequest request,
            AuthenticatedUser currentUser
    ) {
        Business business = managedBusiness(businessId, currentUser);
        business.updatePublicProfile(
                blankToNull(request.publicDescription()),
                blankToNull(request.aboutUs()),
                blankToNull(request.whatsapp()),
                blankToNull(request.instagram())
        );
        return BusinessPublicProfileResponse.from(business);
    }

    @Transactional
    public BusinessImageUploadResponse uploadLogo(
            UUID businessId,
            MultipartFile file,
            AuthenticatedUser currentUser
    ) {
        Business business = managedBusiness(businessId, currentUser);
        ValidatedImage image = validate(file, LOGO_MAX_BYTES);
        String key = imageKey(businessId, "logo", image.extension());
        String previousKey = business.getLogoImageKey();
        storageService.upload(key, image.content(), image.contentType());
        business.updateLogoImageKey(key);
        businessRepository.saveAndFlush(business);
        deletePrevious(previousKey, key);
        return response(key, image);
    }

    @Transactional
    public BusinessImageUploadResponse uploadCover(
            UUID businessId,
            MultipartFile file,
            AuthenticatedUser currentUser
    ) {
        Business business = managedBusiness(businessId, currentUser);
        ValidatedImage image = validate(file, COVER_MAX_BYTES);
        String key = imageKey(businessId, "cover", image.extension());
        String previousKey = business.getCoverImageKey();
        storageService.upload(key, image.content(), image.contentType());
        business.updateCoverImageKey(key);
        businessRepository.saveAndFlush(business);
        deletePrevious(previousKey, key);
        return response(key, image);
    }

    private Business managedBusiness(UUID businessId, AuthenticatedUser currentUser) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        ownershipGuard.requireOwnerOrAdmin(
                business, currentUser, "Business can only be managed by its owner or an admin");
        return business;
    }

    private ValidatedImage validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image exceeds the maximum size of " + (maxBytes / 1024 / 1024) + " MB");
        }
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG, PNG and WEBP images are allowed");
        }
        try {
            byte[] content = file.getBytes();
            if (!matchesSignature(contentType, content)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "File content does not match its image type");
            }
            return new ValidatedImage(content, contentType, extension);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file could not be read");
        }
    }

    private boolean matchesSignature(String contentType, byte[] content) {
        return switch (contentType) {
            case "image/jpeg" -> content.length >= 3
                    && unsigned(content[0]) == 0xff && unsigned(content[1]) == 0xd8 && unsigned(content[2]) == 0xff;
            case "image/png" -> content.length >= 8
                    && unsigned(content[0]) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                    && unsigned(content[4]) == 0x0d && unsigned(content[5]) == 0x0a
                    && unsigned(content[6]) == 0x1a && unsigned(content[7]) == 0x0a;
            case "image/webp" -> content.length >= 12
                    && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private String imageKey(UUID businessId, String type, String extension) {
        return "businesses/%s/%s/%s.%s".formatted(businessId, type, UUID.randomUUID(), extension);
    }

    private void deletePrevious(String previousKey, String newKey) {
        if (previousKey != null && !previousKey.equals(newKey)) {
            storageService.delete(previousKey);
        }
    }

    private BusinessImageUploadResponse response(String key, ValidatedImage image) {
        return new BusinessImageUploadResponse(
                key,
                storageService.signedGetUrl(key),
                image.contentType(),
                image.content().length
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ValidatedImage(byte[] content, String contentType, String extension) {
    }
}
