package com.turnero.service;

import com.turnero.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1")
public class ServiceOfferingController {

    private final ServiceOfferingService offeringService;

    public ServiceOfferingController(ServiceOfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @PostMapping("/businesses/{businessId}/service-offerings")
    ResponseEntity<ServiceOfferingResponse> create(
            @PathVariable UUID businessId,
            @Valid @RequestBody ServiceOfferingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        ServiceOfferingResponse response = offeringService.create(businessId, request, currentUser);
        return ResponseEntity.created(URI.create("/api/v1/service-offerings/" + response.id())).body(response);
    }

    @GetMapping("/businesses/{businessId}/service-offerings")
    List<ServiceOfferingResponse> findPublicByBusiness(@PathVariable UUID businessId) {
        return offeringService.findPublicByBusiness(businessId);
    }

    @GetMapping("/service-offerings/{id}")
    ServiceOfferingResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return offeringService.get(id, currentUser);
    }

    @PutMapping("/service-offerings/{id}")
    ServiceOfferingResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceOfferingRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return offeringService.update(id, request, currentUser);
    }

    @DeleteMapping("/service-offerings/{id}")
    ServiceOfferingResponse deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return offeringService.deactivate(id, currentUser);
    }
}
