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

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(final BusinessService businessService) {
        this.businessService = businessService;
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

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(
            @PathVariable final UUID id,
            @AuthenticationPrincipal final AuthenticatedUser currentUser
    ) {
        this.businessService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
