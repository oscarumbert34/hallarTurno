package com.turnero.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.turnero.auth.AuthenticatedUser;
import com.turnero.common.ApiException;
import com.turnero.security.OwnershipGuard;
import com.turnero.storage.ObjectStorageService;
import com.turnero.user.User;
import com.turnero.user.UserRole;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class BusinessPublicProfileServiceTests {

    private final BusinessRepository businessRepository = org.mockito.Mockito.mock(BusinessRepository.class);
    private final ObjectStorageService storageService = org.mockito.Mockito.mock(ObjectStorageService.class);
    private final BusinessPublicProfileService service = new BusinessPublicProfileService(
            businessRepository, new OwnershipGuard(), storageService);

    @Test
    void uploadsLogoPersistsKeyAndDeletesPreviousObject() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Business business = business(ownerId);
        business.updateLogoImageKey("businesses/old-logo.png");
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        when(businessRepository.saveAndFlush(any(Business.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.signedGetUrl(any())).thenReturn("https://signed.example/logo");
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1});

        BusinessImageUploadResponse response = service.uploadLogo(business.getId(), file, user(ownerId));

        assertThat(response.imageKey()).startsWith("businesses/" + business.getId() + "/logo/").endsWith(".png");
        assertThat(response.imageUrl()).isEqualTo("https://signed.example/logo");
        assertThat(business.getLogoImageKey()).isEqualTo(response.imageKey());
        verify(storageService).upload(response.imageKey(), file.getBytes(), "image/png");
        verify(storageService).delete("businesses/old-logo.png");
        verify(businessRepository).saveAndFlush(business);
    }

    @Test
    void rejectsSpoofedAndOversizedLogoBeforeUploading() {
        UUID ownerId = UUID.randomUUID();
        Business business = business(ownerId);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "logo.png", "image/png", "not-a-png".getBytes());
        assertThatThrownBy(() -> service.uploadLogo(business.getId(), spoofed, user(ownerId)))
                .isInstanceOf(ApiException.class)
                .hasMessage("File content does not match its image type");

        byte[] tooLarge = new byte[1024 * 1024 + 1];
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "logo.png", "image/png", tooLarge);
        assertThatThrownBy(() -> service.uploadLogo(business.getId(), oversized, user(ownerId)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Image exceeds the maximum size of 1 MB");

        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void rejectsUploadFromAnotherBusinessOwner() {
        UUID ownerId = UUID.randomUUID();
        Business business = business(ownerId);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertThatThrownBy(() -> service.uploadLogo(business.getId(), file, user(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .hasMessage("Business can only be managed by its owner or an admin");

        verify(storageService, never()).upload(any(), any(), any());
    }

    @Test
    void updatesOptionalPublicProfileFieldsAndNormalizesBlanks() {
        UUID ownerId = UUID.randomUUID();
        Business business = business(ownerId);
        when(businessRepository.findById(business.getId())).thenReturn(Optional.of(business));

        BusinessPublicProfileResponse response = service.update(
                business.getId(),
                new BusinessPublicProfileRequest(" Descripción ", " Nosotros ", "  ", " barberia "),
                user(ownerId));

        assertThat(response.publicDescription()).isEqualTo("Descripción");
        assertThat(response.aboutUs()).isEqualTo("Nosotros");
        assertThat(response.whatsapp()).isNull();
        assertThat(response.instagram()).isEqualTo("barberia");
    }

    private Business business(UUID ownerId) {
        User owner = User.create("owner@example.com", "hash", UserRole.BUSINESS);
        ReflectionTestUtils.setField(owner, "id", ownerId);
        Business business = Business.create(
                owner, "Barbería", null, null, null, "barberia", BusinessStatus.ACTIVE);
        ReflectionTestUtils.setField(business, "id", UUID.randomUUID());
        return business;
    }

    private AuthenticatedUser user(UUID id) {
        return new AuthenticatedUser(id, "owner@example.com", Set.of(UserRole.BUSINESS));
    }
}
