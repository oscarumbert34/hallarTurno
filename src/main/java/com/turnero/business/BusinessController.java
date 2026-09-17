package com.turnero.business;

import com.turnero.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final BusinessPublicProfileService publicProfileService;

    public BusinessController(
            final BusinessService businessService,
            final BusinessPublicProfileService publicProfileService
    ) {
        this.businessService = businessService;
        this.publicProfileService = publicProfileService;
    }

    @PostMapping
    ResponseEntity<BusinessResponse> create(
            @Valid @RequestBody final BusinessRequest request,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        final BusinessResponse response = this.businessService.create(request, currentUser);
        return ResponseEntity.created(URI.create("/api/v1/businesses/" + response.id())).body(response);
    }

    @GetMapping
    List<PublicBusinessResponse> findPublic() {
        return this.businessService.findPublic();
    }

    @GetMapping("/{id}")
    BusinessResponse get(
            @PathVariable final UUID id,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.businessService.get(id, currentUser);
    }

    @PutMapping("/{id}")
    BusinessResponse update(
            @PathVariable final UUID id,
            @Valid @RequestBody final BusinessRequest request,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.businessService.update(id, request, currentUser);
    }

    @GetMapping("/{id}/configuration")
    BusinessConfigurationResponse getConfiguration(
            @PathVariable final UUID id,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.businessService.getConfiguration(id, currentUser);
    }

    @PutMapping("/{id}/configuration")
    BusinessConfigurationResponse updateConfiguration(
            @PathVariable final UUID id,
            @Valid @RequestBody final BusinessConfigurationRequest request,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.businessService.updateConfiguration(id, request, currentUser);
    }

    @PutMapping("/{id}/public-profile")
    BusinessPublicProfileResponse updatePublicProfile(
            @PathVariable final UUID id,
            @Valid @RequestBody final BusinessPublicProfileRequest request,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.publicProfileService.update(id, request, currentUser);
    }

    @PostMapping(value = "/{id}/public-profile/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BusinessImageUploadResponse uploadLogo(
            @PathVariable final UUID id,
            @RequestPart("file") final MultipartFile file,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.publicProfileService.uploadLogo(id, file, currentUser);
    }

    @PostMapping(value = "/{id}/public-profile/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BusinessImageUploadResponse uploadCover(
            @PathVariable final UUID id,
            @RequestPart("file") final MultipartFile file,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        return this.publicProfileService.uploadCover(id, file, currentUser);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable final UUID id,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        this.businessService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
