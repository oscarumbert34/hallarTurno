package com.turnero.employee;

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
public class BookableResourceController {

    private final BookableResourceService resourceService;

    public BookableResourceController(BookableResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping("/branches/{branchId}/resources")
    ResponseEntity<BookableResourceResponse> create(
            @PathVariable UUID branchId,
            @Valid @RequestBody BookableResourceRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        BookableResourceResponse response = resourceService.create(branchId, request, currentUser);
        return ResponseEntity.created(URI.create("/api/v1/resources/" + response.id())).body(response);
    }

    @GetMapping("/branches/{branchId}/resources")
    List<BookableResourceResponse> findByBranch(
            @PathVariable UUID branchId,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return resourceService.findByBranch(branchId, currentUser);
    }

    @GetMapping("/resources/{id}")
    BookableResourceResponse get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return resourceService.get(id, currentUser);
    }

    @PutMapping("/resources/{id}")
    BookableResourceResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody BookableResourceRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return resourceService.update(id, request, currentUser);
    }

    @DeleteMapping("/resources/{id}")
    BookableResourceResponse deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return resourceService.deactivate(id, currentUser);
    }
}
